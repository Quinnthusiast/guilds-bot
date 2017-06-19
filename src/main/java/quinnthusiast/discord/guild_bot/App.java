package quinnthusiast.discord.guild_bot;

/**
 * Hello world!
 *
 */
public class App 
{
    private static GuildBot bot;
    
    public static void main( String[] args )
    {
        bot = new GuildBot(args[0]);
    }
    
    public static GuildBot getBot()
    {
        return bot;
    }
}
