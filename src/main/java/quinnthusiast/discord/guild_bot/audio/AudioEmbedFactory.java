package quinnthusiast.discord.guild_bot.audio;

import java.util.concurrent.*;
import com.sedmelluq.discord.lavaplayer.track.*;
import quinnthusiast.discord.guild_bot.*;
import sx.blah.discord.api.internal.json.objects.*;
import sx.blah.discord.handle.obj.*;
import sx.blah.discord.util.*;

public class AudioEmbedFactory
{
    private static final int COLOR = 0xe52d27;
    private static final String MAIN_ICON = "https://www.youtube.com/yt/brand/media/image/YouTube-icon-full_color.png";
    private static final String DURATION = "Duration";
    
    private static IUser me = null;
    private static String myAvatarUrl;
    private static String myHandle;
    
    private static void lazyMe()
    {
        if (me == null)
        {
            while ((me = App.getBot().getClient().getUserByID(153885264987553792L)) == null);
            
            myAvatarUrl = me.getAvatarURL();
            myHandle = me.getName() + "#" + me.getDiscriminator();
        }
    }
    
    public static EmbedBuilder newBuilder()
    {
        lazyMe();
        return new TimestampedEmbedBuilder()
                .withColor(COLOR)
                
                .withAuthorIcon(MAIN_ICON)
                .withThumbnail(MAIN_ICON)
                
                .withFooterIcon(myAvatarUrl)
                .withFooterText(myHandle);
    }
    
    public static EmbedObject create(String text, String url, String title, String artist, long duration)
    {
        lazyMe();
        
        String trackTime = String.format("%02d:%02d", TimeUnit.MILLISECONDS.toMinutes(duration), TimeUnit.MILLISECONDS.toSeconds(duration) % 60);
        
        return newBuilder()
                .withAuthorName(title)
                
                .withUrl(url)
                
                .withDesc(text + '\u2014')
                
                .appendField(title, artist, true)
                .appendField(trackTime, DURATION, true)
                
                .build();
    }
    
    public static EmbedObject create(String text, AudioTrackInfo info)
    {
        return ((info != null) ? create(text, info.uri, info.title, info.author, info.length) : newBuilder().withAuthorName(text + '\u2014').build());
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
