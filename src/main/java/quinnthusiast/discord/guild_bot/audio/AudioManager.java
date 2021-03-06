
package quinnthusiast.discord.guild_bot.audio;

import static quinnthusiast.discord.guild_bot.audio.AudioEmbedFactory.*;
import java.util.*;
import java.util.concurrent.*;
import com.sedmelluq.discord.lavaplayer.player.*;
import com.sedmelluq.discord.lavaplayer.source.*;
import com.sedmelluq.discord.lavaplayer.tools.*;
import sx.blah.discord.handle.obj.*;

public enum AudioManager // implements AudioEventHandler
{
    INSTANCE;

    /**
     * Amount to buffer in milliseconds,
     * as well as how long it takes to send a stuck event
     */
    private static final int BUFFER = 15000;
    
    private static final String MUSIC_QUEUE_CHANNEL_NAME = "music-queue";

    private final AudioPlayerManager apm;
    private final Map<IGuild, AudioScheduler> guildSchedulers;
    private final Map<IUser, UserTrackQueue> userTrackLists;

    private AudioManager()
    {
        apm = new DefaultAudioPlayerManager();
        apm.setFrameBufferDuration(BUFFER);
        apm.setTrackStuckThreshold(BUFFER);
        AudioSourceManagers.registerRemoteSources(apm);
        
        guildSchedulers = new ConcurrentHashMap<>();
        userTrackLists = new ConcurrentHashMap<>();
    }
    
    public AudioScheduler getSchedulerForGuild(IGuild g)
    {
        if (guildSchedulers.containsKey(g)) 
        {
            return guildSchedulers.get(g);
        }
        else
        {
            IChannel channel;
            try
            {
                channel = g.getChannelsByName(MUSIC_QUEUE_CHANNEL_NAME).get(0);
            }
            catch (IndexOutOfBoundsException e)
            { // no dedicated music channel found, use #general
                channel = g.getGeneralChannel();
            }
            
            guildSchedulers.put(g, new AudioScheduler(apm.createPlayer(), channel));
            return getSchedulerForGuild(g);
        }
    }
    
    public UserTrackQueue getTrackQueueForUser(IUser u)
    {
        if (userTrackLists.containsKey(u))
        {
            return userTrackLists.get(u);
        }
        else
        {
            userTrackLists.put(u, new UserTrackQueue(u));
            return getTrackQueueForUser(u);
        }
    }
    
    public Future<Void> load(IChannel channel, String identifier, IUser requester)
    {
        UserTrackQueue.TrackLoader loader = getTrackQueueForUser(requester).new TrackLoader()
                .setOnTrackLoaded(track -> channel.sendMessage(create("Added track to your queue", track.getInfo(), requester).build()))
                .setOnPlaylistLoaded(playlist -> channel.sendMessage(create("Added playlist to your queue", null, requester).build()))
                .setOnLoadFailed(exception -> channel.sendMessage(create("Load failed" + ((exception.severity != FriendlyException.Severity.COMMON) ? ": " + exception.severity.toString() : ""), null, requester).build()))
                .setOnNoMatches(() -> channel.sendMessage(create("No matches for requested track", null, requester).build()));
        return apm.loadItemOrdered(getSchedulerForGuild(channel.getGuild()), identifier, loader);
    }
    
    /**
     * Checks if a channel is the music requests channel in a guild<br/>
     * 
     * Returns <code>true</code> if:<br/><ul>
     * <li>The channel is named <code>music-queue</code>, or if<br/>
     * <li>There are no channels called <code>music-queue</code> in its guild and it also is default channel</ul>
     * @param c The channel to check
     * @return true if music requests channel
     */
    public static boolean isMusicChannel(IChannel c)
    {
        return MUSIC_QUEUE_CHANNEL_NAME.equals(c.getName()) || (c.getGuild().getChannelsByName(MUSIC_QUEUE_CHANNEL_NAME).isEmpty() && c.getGuild().getGeneralChannel().equals(c));
    }
    
}
