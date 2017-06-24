package quinnthusiast.discord.guild_bot.audio;

import java.util.*;
import java.util.concurrent.*;
import java.util.function.*;
import com.sedmelluq.discord.lavaplayer.track.*;
import sx.blah.discord.api.internal.json.objects.*;
import sx.blah.discord.handle.obj.*;
import sx.blah.discord.util.*;

public class AudioEmbedFactory
{
    private static final int COLOR = 0xe52d27;
    private static final String MAIN_ICON = "https://www.youtube.com/yt/brand/media/image/YouTube-icon-full_color.png";
    private static final String DURATION = "Duration";
    
    public static EmbedBuilder newBuilder(IUser u)
    {
        EmbedBuilder builder = new TimestampedEmbedBuilder();
        
        if (u != null)
        {
            builder
                .withFooterIcon(u.getAvatarURL())
                .withFooterText(u.getName() + "#" + u.getDiscriminator());
        }
        return builder
                .withColor(COLOR)
                
                .withAuthorIcon(MAIN_ICON);
                //.withThumbnail(MAIN_ICON);
    }
    
    public static EmbedBuilder create(EmbedBuilder chain, String text, String url, String title, String artist, long duration, IUser u)
    {
        if (chain == null)
        {
            chain = newBuilder(u);
        }
        
        return addTrack(chain, title, artist, duration)
                .withAuthorName(text + '\u2014')
                .withUrl(url);
    }
    
    public static EmbedBuilder addTrack(EmbedBuilder b, String title, String artist, long duration)
    {
        String formattedDuration = String.format("%02d:%02d", TimeUnit.MILLISECONDS.toMinutes(duration), TimeUnit.MILLISECONDS.toSeconds(duration) % 60);
        
        return b
                .appendField(title + " ", artist, true)
                .appendField(DURATION, formattedDuration, true);
    }
    
    public static EmbedBuilder create(String text, String url, String title, String artist, long duration, IUser u)
    {
        return create(null, text, url, title, artist, duration, u);
    }
    
    public static EmbedBuilder create(String text, AudioTrackInfo info, IUser u)
    {
        return create(null, text, info, u);
    }
    
    public static EmbedBuilder create(EmbedBuilder chain, String text, AudioTrackInfo info, IUser u)
    {
        return ((info != null) ? create(chain, text, info.uri, info.title, info.author, info.length, u) : newBuilder(u).withAuthorName(text + '\u2014'));
    }
    
    public static EmbedBuilder createMultiple(EmbedBuilder chain, String text, AudioTrackInfo[] info, IUser u) 
    {
        if (chain == null)
        {
            return createMultiple(newBuilder(u), text, info, u);
        }
        
        Consumer<AudioTrackInfo> curry = e -> addTrack(chain, e.title, e.author, e.isStream ? -1 : e.length);
        Arrays.stream(info).forEach(curry);
        
        return chain;
    }
    
    private static class TimestampedEmbedBuilder extends EmbedBuilder
    {
        @Override
        public EmbedObject build()
        {
            this.withTimestamp(System.currentTimeMillis());
            return super.build();
        }
    }
}
