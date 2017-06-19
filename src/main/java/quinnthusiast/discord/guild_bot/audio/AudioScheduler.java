package quinnthusiast.discord.guild_bot.audio;

import static quinnthusiast.discord.guild_bot.audio.AudioEmbedFactory.*;
import java.util.*;
import java.util.concurrent.*;
import com.sedmelluq.discord.lavaplayer.player.*;
import com.sedmelluq.discord.lavaplayer.player.event.*;
import com.sedmelluq.discord.lavaplayer.tools.*;
import com.sedmelluq.discord.lavaplayer.track.*;
import com.sedmelluq.discord.lavaplayer.track.playback.*;
import sx.blah.discord.handle.audio.*;
import sx.blah.discord.handle.obj.*;

public class AudioScheduler extends AudioEventAdapter implements AudioLoadResultHandler, IAudioProvider
{
    private static final String MUSIC_PLAYBACK_CHANNEL_NAME = "Music Channel";
    
    private final Queue<AudioTrack> trackQueue;
    private final AudioPlayer player;
    private final IGuild guild;
    private final IChannel channel;
    private IVoiceChannel playbackChannel;
    private AudioFrame lastFrame;
    
    public AudioScheduler(AudioPlayer player, IChannel channel)
    {
        this.player = player;
        player.addListener(this);
        
        this.guild = channel.getGuild();
        this.channel = channel;
        
        trackQueue = new LinkedBlockingQueue<>();
        
        guild.getAudioManager().setAudioProvider(this);
        Runtime.getRuntime().addShutdownHook(new Thread(this::cleanup));
    }
    
    public void queue(AudioTrack t)
    {
        if (t == null)
        {
            return;
        }
        
        if (player.getPlayingTrack() == null)
        {
            play(t);
        }
        else
        {
            channel.sendMessage(create("Queued track", t.getInfo()));
            trackQueue.offer(t);
        }
    }
   
    public void skip()
    {
        play(trackQueue.poll());
    }
    
   public void cleanup()
   {
       if (!trackQueue.isEmpty())
       {
           trackQueue.removeAll(trackQueue);
       }
       
       if (playbackChannel != null)
       {
           playbackChannel.delete();
           playbackChannel = null;
       }
   }
    
    @Override
    public void onTrackEnd(AudioPlayer player, AudioTrack track, AudioTrackEndReason endReason)
    {
        if (playbackChannel != null && trackQueue.isEmpty() && endReason != AudioTrackEndReason.REPLACED)
        {
            cleanup();
        }
        else if (endReason == AudioTrackEndReason.FINISHED)
        {
            skip();
        }
    }
    
    private void play(AudioTrack t)
    {
        if (playbackChannel == null)
        {
            playbackChannel = guild.createVoiceChannel(MUSIC_PLAYBACK_CHANNEL_NAME);
            playbackChannel.changeBitrate(96000);
            playbackChannel.join();
        }
        
        if (t == null)
        {
            player.playTrack(null);
            return;
        }
        
        channel.sendMessage(create("Now playing", t.getInfo()));
        player.playTrack(t);
    }
    
    @Override
    public void trackLoaded(AudioTrack track)
    {
        queue(track);
    }

    @Override
    public void playlistLoaded(AudioPlaylist playlist)
    {
        playlist.getTracks().forEach(this::trackLoaded);
    }

    @Override
    public void noMatches()
    {
        channel.sendMessage(create("No matches for track", null));
    }

    @Override
    public void loadFailed(FriendlyException exception)
    {
        channel.sendMessage(create("Load failed" + ((exception.severity != FriendlyException.Severity.COMMON) ? ": " + exception.severity.toString() : ""), null));
    }

    @Override
    public boolean isReady()
    {
        if (lastFrame == null)
        {
            lastFrame = player.provide();
        }
        
        return lastFrame != null;
    }

    @Override
    public byte[] provide()
    {
        if (lastFrame == null) 
        {
            lastFrame = player.provide();
        }
        
        byte[] data = lastFrame != null ? lastFrame.data : null;
        lastFrame = null;
        
        return data;
    }
    
    @Override
    public AudioEncodingType getAudioEncodingType()
    {
        // TODO Auto-generated method stub
        return AudioEncodingType.OPUS;
    }
    
}
