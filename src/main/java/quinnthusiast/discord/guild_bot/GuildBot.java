
package quinnthusiast.discord.guild_bot;

import static quinnthusiast.discord.guild_bot.audio.AudioEmbedFactory.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import org.slf4j.*;
import quinnthusiast.discord.guild_bot.audio.*;
import sx.blah.discord.api.*;
import sx.blah.discord.api.events.*;
import sx.blah.discord.handle.impl.events.*;
import sx.blah.discord.handle.impl.events.guild.GuildCreateEvent;
import sx.blah.discord.handle.impl.events.guild.channel.message.MessageReceivedEvent;
import sx.blah.discord.handle.impl.events.guild.member.UserJoinEvent;
import sx.blah.discord.handle.obj.*;
import sx.blah.discord.util.*;

public class GuildBot
{
    private static final String SUB_PREFIX = "sub-";
    
    private IDiscordClient      client;
    private AtomicBoolean       ready;
    private Map<String, String> prefixMap; // can't be <IGuild, String> because IGuild is not serializable
    private Map<String, Long>   memberRoleMap; // ditto
    private ExecutorService     pool;
    

    public GuildBot(String token)
    {
        ready = new AtomicBoolean(false);

        deserializePrefixes();
        deserializeRoles();
        if (prefixMap == null) prefixMap = new HashMap<>();
        if (memberRoleMap == null) memberRoleMap = new HashMap<>();
        pool = Executors.newCachedThreadPool();

        client = new ClientBuilder().withToken(token).registerListeners(this).setMaxReconnectAttempts(-1).login();
    }

    @EventSubscriber
    public void onReadyEvent(ReadyEvent e)
    {
        LoggerFactory.getLogger(getClass()).debug("Bot Loaded");
        ready.set(true);
        
    }
    
    @EventSubscriber
    public void onMessageReceived(MessageReceivedEvent e)
    {
        if (e.getChannel().isPrivate() || e.getAuthor().isBot())
        {
            // do not process PMs here
            // ignore bot messages
            return;
        }

        pool.execute(() -> process(e));
    }

    @EventSubscriber
    public void onUserJoin(UserJoinEvent e)
    {
        IGuild g = e.getGuild();
        
        if (e.getUser().isBot())
        {
            return;
        }
        
        g.getChannelByID(g.getLongID()) // default channel
                .sendMessage("Welcome to " + g.getName() + ", " + e.getUser().mention() + "! Please use `"
                        + prefixMap.get(g.getStringID())
                        + "register <ign>` (without the angle brackets) to register. \nEnjoy your stay with us~");
    }

    @EventSubscriber
    public void onJoinGuild(GuildCreateEvent e)
    {
        if (!ready.get()) return;
        pool.execute(() -> requestPrefix(e.getGuild()));
    }

    private void process(MessageReceivedEvent e)
    {
        // wait for the bot to be ready,
        // must be executed on a pool thread
        // as to not block the dispatcher thread
        while (!ready.get());
        
        IGuild theGuild = e.getGuild();
        String prefix = prefixMap.get(theGuild.getStringID());
        if (prefix == null && !e.getChannel().isPrivate())
        {
            client.getDispatcher().unregisterListener(this);
            requestPrefix(theGuild);
            client.getDispatcher().registerListener(this);
            return;
        }

        if (e.getChannel().isPrivate()) { return; }

        // XXX SCHEDULED FOR REWRITAL
        // WILL IMPLEMENT A NEW COMMAND API IN A FUTURE VERSION
        String content = e.getMessage().getContent();
        if (!content.startsWith(prefix)) { return; }

        String[] sliced = content.substring(prefix.length()).split(" ", 2);
        String command = sliced[0].toLowerCase();
        String args = (sliced.length < 2) ? null : sliced[1];
        
        boolean delete = false;
        IUser author = e.getAuthor();
        if (AudioManager.isMusicChannel(e.getChannel()))
        {
            delete = true;
            AudioManager am = AudioManager.INSTANCE;
            AudioScheduler scheduler = am.getSchedulerForGuild(theGuild);
            switch (command)
            {
                case "lineup":
                    try
                    {
                        scheduler.setOnJoinDjQueue(u -> e.getChannel().sendMessage(create("Lined up", null, u).build()));
                        scheduler.setOnLeaveDjQueue(u -> e.getChannel().sendMessage(create("Left lineup", null, u).build()));
                        scheduler.toggleDj(author);
                    } 
                    catch (IllegalStateException ex)
                    { // user playlist was empty
                        e.getChannel().sendMessage(create("Playlist empty: Failed to join DJ queue", null, author).build());
                    }
                    break;
                    
                case "queue":
                    if ("".equals(args))
                    {
                        break;
                    }
                    AudioManager.INSTANCE.load(e.getChannel(), args, author);
                    break;
                    
                /*case "skip":
                    if ("all".equals(args))
                    {
                        scheduler.cleanup();
                    }
                    else
                    {
                        scheduler.skip();
                    }
                    break;*/
                    
                case "dj":
                    EmbedBuilder queueMessage = create("DJ Queue", null, author);
                    Queue<IUser> djQueue = scheduler.getQueue();
                    IUser dj = djQueue.poll();
                    queueMessage.appendField("Current", dj != null ? dj.getDisplayName(theGuild) : "None",  true);
                    dj = djQueue.poll();
                    queueMessage.appendField("Up next", dj != null ? dj.getDisplayName(theGuild) : "None", true);
                    
                    djQueue.forEach(i -> queueMessage.appendField("After", i.getDisplayName(theGuild), false));
                    e.getChannel().sendMessage(queueMessage.build());
                    break;
                    
                case "song":
                    e.getChannel().sendMessage(create("Currently playing", scheduler.getCurrentTrack().getInfo(), author).build());
                    break;
                
                default: delete = false;
            }
        }
        
        if (!delete)
        {
            switch (command)
            {
                // XXX DEBUG COMMAND
                /*case "murder":
                    e.getMessage().delete();
                    System.exit(0);
                    break;*/
                
                case "register":
                    delete = register(theGuild, author, args);
                    break;

                case "setrole":
                    delete = setRole(e.getMessage().getChannel(), author, args);
                    break;

                case "sub":
                    delete = subscribe(theGuild, author, args);
                    break;

                default: return;
            }
        }
        if (delete)
        {
            e.getMessage().delete();
        }
    }

    private boolean subscribe(IGuild g, IUser author, String args)
    {
        if (args == null)
        {
            return false;
        }
        
        args = args.trim().toLowerCase();
        
        List<IRole> matching = g.getRolesByName(SUB_PREFIX + args);
        
        if (matching.isEmpty())
        {
            IRole subRole = g.createRole();
            subRole.changeName(SUB_PREFIX + args);
            subRole.changeMentionable(true);
            return subscribe(g, author, args);
        }
        else if (matching.size() > 1)
        {
            matching.forEach(IRole::delete);
            return subscribe(g, author, args);
        }
        
        IRole subRole = matching.get(0);
        if (g.getRolesForUser(author).contains(subRole))
        {
            if (g.getUsersByRole(subRole).size() <= 1)
            {
                subRole.delete();
            }
            else
            {
                author.removeRole(subRole);
            }
        }
        else
        {
            author.addRole(subRole);
        }
        return true;
    }

    private boolean register(IGuild g, IUser author, String args)
    {
        if ("".equals(args)) { return false; }
        
        try
        {
            g.setUserNickname(author, args);
        }
        catch (Exception e)
        {
            System.err.println("nickexc");
            return false;
        }
        
        try
        {
            IRole r = g.getRoleByID(memberRoleMap.get(g.getStringID()));
            if (r != null)
            {
                author.addRole(r);
            }
        }
        catch (Exception e)
        {
            e.printStackTrace();
            System.err.println("roleexc");
            return false;
        }
        
        return true;
    }

    private boolean setRole(IChannel c, IUser author, String args)
    {
        if ("".equals(args)) { return false; }
        if (!c.getGuild().getOwner().equals(author)) { return false; }

        List<IRole> match = c.getGuild().getRolesByName(args);
        if (match.isEmpty())
        {
            c.sendMessage("no matching roles found");
        }
        else
        {
            memberRoleMap.put(c.getGuild().getStringID(), match.get(0).getLongID());
            serializeRoles();
        }
        
        return true;
    }

    private void serializePrefixes()
    {
        try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream("prefix.ser")))
        {
            oos.writeObject(prefixMap);
        }
        catch (FileNotFoundException e)
        {
            File f = new File("prefix.ser");
            if (!f.mkdir()) { throw new RuntimeException("cannot make file ".concat(f.getPath())); }
            serializePrefixes();
        }
        catch (IOException e)
        {
            e.printStackTrace();
        }
    }

    @SuppressWarnings("unchecked")
    private void deserializePrefixes()
    {
        try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream("prefix.ser")))
        {
            prefixMap = (Map<String, String>) ois.readObject();
        }
        catch (FileNotFoundException e)
        {
            e.printStackTrace();
        }
        catch (IOException e)
        {
            e.printStackTrace();
        }
        catch (ClassNotFoundException e)
        {
            e.printStackTrace();
        }
    }

    private void serializeRoles()
    {
        try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream("role.ser")))
        {
            oos.writeObject(memberRoleMap);
        }
        catch (FileNotFoundException e)
        {
            File f = new File("prefix.ser");
            if (!f.mkdir()) { throw new RuntimeException("cannot make file ".concat(f.getPath())); }
            serializePrefixes();
        }
        catch (IOException e)
        {
            e.printStackTrace();
        }
    }

    @SuppressWarnings("unchecked")
    private void deserializeRoles()
    {
        try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream("role.ser")))
        {
            memberRoleMap = (Map<String, Long>) ois.readObject();
        }
        catch (FileNotFoundException e)
        {
            e.printStackTrace();
        }
        catch (IOException e)
        {
            e.printStackTrace();
        }
        catch (ClassNotFoundException e)
        {
            e.printStackTrace();
        }
    }

    private void requestPrefix(IGuild guild)
    {
        IPrivateChannel pmWithOwner = guild.getOwner().getOrCreatePMChannel();
        pmWithOwner.sendMessage(
                "Hey guild owner! Please set a prefix to use! Something like `!` or `?`, but you could use anything, really. Keep in mind that popular prefixes (especially `!`) might be incompatible with other bots.");
        IMessage response = Requester.waitForMessage(pmWithOwner);
        pmWithOwner.sendMessage("Set prefix: `".concat(response.getContent()).concat("`"));
        prefixMap.put(guild.getStringID(), response.getContent());
        serializePrefixes();
    }
    
    public IDiscordClient getClient()
    {
        return client;
    }

}
