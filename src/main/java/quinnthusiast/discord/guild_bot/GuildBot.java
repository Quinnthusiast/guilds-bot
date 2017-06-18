
package quinnthusiast.discord.guild_bot;

import java.io.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import sx.blah.discord.api.*;
import sx.blah.discord.api.events.*;
import sx.blah.discord.handle.impl.events.*;
import sx.blah.discord.handle.impl.events.guild.GuildCreateEvent;
import sx.blah.discord.handle.impl.events.guild.channel.message.MessageReceivedEvent;
import sx.blah.discord.handle.impl.events.guild.member.UserJoinEvent;
import sx.blah.discord.handle.obj.*;

public class GuildBot
{

    private static final String SUB_PREFIX = "sub-";
    
    private IDiscordClient      c;
    private AtomicBoolean       ready;
    private Map<String, String> prefixMap;
    private Map<String, Long>   memberRoleMap;
    private ExecutorService     pool;
    

    public GuildBot(String token)
    {
        ready = new AtomicBoolean(false);

        deserializePrefixes();
        deserializeRoles();
        if (prefixMap == null) prefixMap = new HashMap<>();
        if (memberRoleMap == null) memberRoleMap = new HashMap<>();
        pool = Executors.newCachedThreadPool();

        c = new ClientBuilder().withToken(token).registerListeners(this).login();

    }

    @EventSubscriber
    public void onReadyEvent(ReadyEvent e)
    {
        ready.set(true);
    }

    @EventSubscriber
    public void onMessageReceived(MessageReceivedEvent e)
    {
        if (e.getChannel().isPrivate())
        {
            // do not process PMs here
            return;
        }

        if (!ready.get()) return;
        pool.execute(() -> process(e));
    }

    @EventSubscriber
    public void onUserJoin(UserJoinEvent e)
    {
        IGuild g = e.getGuild();
        g.getChannelByID(g.getLongID())
                .sendMessage("Welcome to " + g.getName() + ", " + e.getUser().getDisplayName(g) + "! Please use `"
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
        IGuild theGuild = e.getGuild();
        String prefix = prefixMap.get(theGuild.getStringID());
        if (prefix == null && !e.getChannel().isPrivate())
        {
            c.getDispatcher().unregisterListener(this);
            requestPrefix(theGuild);
            c.getDispatcher().registerListener(this);
            return;
        }

        if (e.getChannel().isPrivate()) { return; }

        // I know this is a cringy command interpreter but the bot only needs like two
        // commands
        String content = e.getMessage().getContent();
        if (!content.startsWith(prefix)) { return; }

        String[] sliced = content.substring(prefix.length()).split(" ");
        
        boolean delete = false;
        switch (sliced[0].toLowerCase())
        {
            case "register":
                delete = register(theGuild, e.getAuthor(), sliced);
                break;
                
            case "setrole":
                delete = setRole(e.getMessage().getChannel(), e.getAuthor(), sliced);
                break;
                
            case "sub":
                delete = subscribe(theGuild, e.getAuthor(), sliced);
                break;
        }

        if (delete)
        {
            e.getMessage().delete();
        }
    }

    private boolean subscribe(IGuild g, IUser author, String[] args)
    {
        if (args.length < 1) return false;
        
        try
        {
            IRole subRole = g.getRolesByName(SUB_PREFIX + args[1]).get(0);
            
            if (author.getRolesForGuild(g).contains(subRole))
            {
                author.removeRole(subRole);
                
                if (g.getUsersByRole(subRole).isEmpty())
                {
                    subRole.delete();
                }
            }
            else
            {
                author.addRole(subRole);
            }
        }
        catch(IndexOutOfBoundsException e)
        { // Role not found, make it!
            IRole newSubRole = g.createRole();
            newSubRole.changeMentionable(true);
            newSubRole.changeName(SUB_PREFIX + args[1]);
            
            // now that the role has been made, process oncemore
            return subscribe(g, author, args);
        }
        return true;
    }

    private boolean register(IGuild g, IUser author, String[] args)
    {
        if (args.length < 1) { return false; }
        
        String ign = args[1];
        
        if (args.length >= 3)
        {
            StringBuilder b = new StringBuilder(ign);
            for (int i = 2; i < args.length; ++i)
            {
                b.append(' ');
                b.append(args[i]);
            }
            
            ign = b.toString();
        }
        
        try
        {
            g.setUserNickname(author, ign);
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

    private boolean setRole(IChannel c, IUser author, String[] args)
    {
        if (args.length < 1) { return false; }
        if (!c.getGuild().getOwner().equals(author)) { return false; }

        List<IRole> match = c.getGuild().getRolesByName(args[1]);
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

}
