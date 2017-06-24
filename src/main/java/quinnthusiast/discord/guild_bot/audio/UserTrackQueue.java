package quinnthusiast.discord.guild_bot.audio;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.function.*;
import com.sedmelluq.discord.lavaplayer.player.*;
import com.sedmelluq.discord.lavaplayer.tools.*;
import com.sedmelluq.discord.lavaplayer.track.*;
import sx.blah.discord.handle.obj.*;

public class UserTrackQueue implements Queue<AudioTrack>
{
    private final IUser owner;
    private final Queue<AudioTrack> trackList; 
    private final AtomicBoolean looping;
    //private final IChannel channel;
    
    public UserTrackQueue(IUser owner)
    {
        this.owner = owner;
        trackList = new LinkedBlockingQueue<>();
        looping = new AtomicBoolean(false);
    }
    
    public IUser getOwner()
    {
        return owner;
    }
    
    public boolean isLooping()
    {
        return looping.get();
    }
    
    public void setIsLooping(boolean b)
    {
        looping.set(b);
    }
    
    public class TrackLoader implements AudioLoadResultHandler
    {
        private Consumer<AudioTrack> onTrackLoaded;
        private Consumer<AudioPlaylist> onPlaylistLoaded;
        private Consumer<FriendlyException> onLoadFailed;
        private Runnable onNoMatches;
        
        public TrackLoader setOnTrackLoaded(Consumer<AudioTrack> onTrackLoaded)
        {
            this.onTrackLoaded = onTrackLoaded;
            return this;
        }
        
        public TrackLoader setOnPlaylistLoaded(Consumer<AudioPlaylist> onPlaylistLoaded)
        {
            this.onPlaylistLoaded = onPlaylistLoaded;
            return this;
        }
        
        public TrackLoader setOnLoadFailed(Consumer<FriendlyException> onLoadFailed)
        {
            this.onLoadFailed = onLoadFailed;
            return this;
        }
        
        public TrackLoader setOnNoMatches(Runnable onNoMatches)
        {
            this.onNoMatches = onNoMatches;
            return this;
        }
        
        @Override
        public void trackLoaded(AudioTrack track)
        {
            if (onTrackLoaded != null)
            {
                onTrackLoaded.accept(track);
            }
            trackList.offer(track);
            // channel.sendMessage(create("Added track to your queue", track.getInfo(), owner).build());
        }

        @Override
        public void playlistLoaded(AudioPlaylist playlist)
        {
            if (onPlaylistLoaded != null)
            {
                onPlaylistLoaded.accept(playlist);
            }
            playlist.getTracks().forEach(trackList::offer);
            // channel.sendMessage(create("Added playlist to your queue", null, owner).build());
        }

        @Override
        public void loadFailed(FriendlyException exception)
        {
            if (onLoadFailed != null)
            {
                onLoadFailed.accept(exception);
            }
            //channel.sendMessage(create("Load failed" + ((exception.severity != FriendlyException.Severity.COMMON) ? ": " + exception.severity.toString() : ""), null, owner).build());
        }
        
        @Override
        public void noMatches()
        {
            if (onNoMatches != null)
            {
                onNoMatches.run();
            }
            // channel.sendMessage(create("No matches for requested track track", null, owner).build());
        }
    }
    
    /////////////////////
    /// QUEUE METHODS ///
    /////////////////////

    @Override
    public int size()
    {
        return trackList.size();
    }

    @Override
    public boolean isEmpty()
    {
        return trackList.isEmpty();
    }

    @Override
    public boolean contains(Object o)
    {
        return trackList.contains(o);
    }

    @Override
    public Iterator<AudioTrack> iterator()
    {
        return trackList.iterator();
    }

    @Override
    public Object[] toArray()
    {
        return trackList.toArray();
    }

    @Override
    public <T> T[] toArray(T[] a)
    {
        return trackList.toArray(a);
    }

    @Override
    public boolean remove(Object o)
    {
        return trackList.remove(o);
    }

    @Override
    public boolean containsAll(Collection<?> c)
    {
        return trackList.containsAll(c);
    }

    @Override
    public boolean addAll(Collection<? extends AudioTrack> c)
    {
        return trackList.addAll(c);
    }

    @Override
    public boolean removeAll(Collection<?> c)
    {
        return trackList.removeAll(c);
    }

    @Override
    public boolean retainAll(Collection<?> c)
    {
        return trackList.retainAll(c);
    }

    @Override
    public void clear()
    {
        trackList.clear();
    }

    @Override
    public boolean add(AudioTrack e)
    {
        return trackList.add(e);
    }

    @Override
    public boolean offer(AudioTrack e)
    {
        return trackList.offer(e);
    }

    @Override
    public AudioTrack remove()
    {
        return trackList.remove();
    }

    @Override
    public AudioTrack poll()
    {
        return trackList.poll();
    }

    @Override
    public AudioTrack element()
    {
        return trackList.element();
    }

    @Override
    public AudioTrack peek()
    {
        return trackList.peek();
    }
}
