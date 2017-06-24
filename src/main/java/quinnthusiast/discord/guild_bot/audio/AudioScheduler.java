package quinnthusiast.discord.guild_bot.audio;

import static quinnthusiast.discord.guild_bot.audio.AudioEmbedFactory.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.*;
import com.sedmelluq.discord.lavaplayer.player.*;
import com.sedmelluq.discord.lavaplayer.player.event.*;
import com.sedmelluq.discord.lavaplayer.track.*;
import com.sedmelluq.discord.lavaplayer.track.playback.*;
import sx.blah.discord.handle.audio.*;
import sx.blah.discord.handle.obj.*;

public class AudioScheduler extends AudioEventAdapter implements IAudioProvider
{
    private static final String MUSIC_PLAYBACK_CHANNEL_NAME = "Music Channel";
    
    private final Queue<IUser> djQueue;
    private final AudioPlayer player;
    private final IGuild guild;
    private final IChannel channel;
    private IVoiceChannel playbackChannel;
    private AudioFrame lastFrame;
    private Consumer<IUser> onJoinDjQueue, onLeaveDjQueue;
    private Runnable onSkip;

    
    public AudioScheduler(AudioPlayer player, IChannel channel)
    {
        this.player = player;
        player.addListener(this);
        
        this.guild = channel.getGuild();
        this.channel = channel;
        
        djQueue = new LinkedBlockingQueue<>();
        
        
        //channel.getClient().getDispatcher().registerListener(this);
        guild.getAudioManager().setAudioProvider(this);
        Runtime.getRuntime().addShutdownHook(new Thread(this::cleanup));
    }
    
    public void toggleDj(IUser u) throws IllegalStateException
    {
        if (djQueue.contains(u))
        {
            leaveDjQueue(u);
        }
        else
        {
            joinDjQueue(u);
        }
    }
    
    public void setOnJoinDjQueue(Consumer<IUser> onJoinDjQueue)
    {
        this.onJoinDjQueue = onJoinDjQueue;
    }
    
    public boolean joinDjQueue(IUser u) throws IllegalStateException
    {
        if (AudioManager.INSTANCE.getTrackQueueForUser(u).isEmpty())
        {
            throw new IllegalStateException("Empty queue");
        }
        
        // check if this is the first dj to be added
        boolean isFirstDj = djQueue.isEmpty();
        boolean ret = djQueue.add(u);
        if (!ret)
        {
            return false;
        }
        
        if (onJoinDjQueue != null)
        {
            onJoinDjQueue.accept(u);
        }
        
        if (isFirstDj)
        { // if the queue is empty start playing
            // by fetching the dj's first track 
            play(fetch());
        }
        
        return ret;
    }
    
    public void setOnLeaveDjQueue(Consumer<IUser> onLeaveDjQueue)
    {
        this.onLeaveDjQueue = onLeaveDjQueue;
    }
    
    public boolean leaveDjQueue(IUser u)
    {
        if (onLeaveDjQueue != null)
        {
            onLeaveDjQueue.accept(u);
        }
        
        boolean isCurrentDj = u.equals(djQueue.peek());
        boolean ret = djQueue.remove(u);
        
        if (isCurrentDj) 
        {
            skip();
        }
        
        return ret;
    }
    
    public Queue<IUser> getQueue()
    {
        Queue<IUser> ret = new LinkedList<>(djQueue);
        return ret;
    }
    
    public AudioTrack getCurrentTrack()
    {
        return player.getPlayingTrack();
    }
    
    
    private AudioTrack fetch()
    {
        if (djQueue.isEmpty())
        {
            // if there are no DJs we are unable to fetch a track
            return null;
        }
        
        djQueue.add(djQueue.poll()); // re-add the dj being replaced
        IUser theDj = djQueue.peek(); // fetch the replacing dj
        AudioTrack nextTrack = AudioManager.INSTANCE.getTrackQueueForUser(theDj).poll(); // fetch his next song
        
        
        if (nextTrack == null)
        { // if his playlist is empty
            // recursively fetch the next element
            // (get the next DJ's queued song)
            // and remove him from the queue (empty list = goner)
            IUser leaver = djQueue.poll();
            if(onLeaveDjQueue != null)
            {
                onLeaveDjQueue.accept(leaver);
            }
            
            return fetch();
        }
        else
        { // if his playlist isn't empty
            return nextTrack; // return the fetched track
        }
    }
    
    public void setOnSkip(Runnable onSkip)
    {
        this.onSkip = onSkip;
    }
   
    public void skip()
    {
        if (onSkip != null)
        {
            onSkip.run();
        }
        
        
        play(fetch());
    }
    
    public void startup()
    {
        if (playbackChannel == null)
        {
            guild.getVoiceChannelsByName(MUSIC_PLAYBACK_CHANNEL_NAME).forEach(IChannel::delete);;
            
            playbackChannel = guild.createVoiceChannel(MUSIC_PLAYBACK_CHANNEL_NAME);
            playbackChannel.changeBitrate(96000);
            
            playbackChannel.overrideRolePermissions(guild.getEveryoneRole(), null, EnumSet.of(Permissions.VOICE_SPEAK));
            playbackChannel.overrideUserPermissions(guild.getClient().getOurUser(), EnumSet.of(Permissions.VOICE_SPEAK), null);
            playbackChannel.join();
        }
    }

    public void cleanup()
    {
        if (playbackChannel != null)
        {
           //while (!isReady()); // block until not ready anymore (??)
            
            if (playbackChannel.isConnected())
            {
                try
                {
                    new FutureTask<Void>(() -> { playbackChannel.leave(); return null; }).get();
                }
                catch (InterruptedException | ExecutionException e)
                {
                    e.printStackTrace();
                }
            }
            playbackChannel = null;
        }
        
        guild.getVoiceChannelsByName(MUSIC_PLAYBACK_CHANNEL_NAME).forEach(IChannel::delete);
    }
    
    @Override
    public void onTrackEnd(AudioPlayer player, AudioTrack track, AudioTrackEndReason endReason)
    {
        if (endReason == AudioTrackEndReason.REPLACED)
        {
            
        }
        play(fetch());
    }
    
    private void play(AudioTrack t)
    {
        startup();
        
        if (t == null)
        {
            player.playTrack(null);
            return;
        }
        
        channel.sendMessage(create("Now playing", t.getInfo(), djQueue.peek()).build());
        player.playTrack(t);
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
        return AudioEncodingType.OPUS;
    }
}
