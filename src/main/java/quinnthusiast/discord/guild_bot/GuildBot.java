package quinnthusiast.discord.guild_bot;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import sx.blah.discord.api.ClientBuilder;
import sx.blah.discord.api.events.EventSubscriber;
import sx.blah.discord.handle.impl.events.guild.channel.message.MessageReceivedEvent;
import sx.blah.discord.handle.obj.IGuild;
import sx.blah.discord.handle.obj.IMessage;
import sx.blah.discord.handle.obj.IPrivateChannel;
import sx.blah.discord.handle.obj.IRole;
import sx.blah.discord.handle.obj.IUser;

public class GuildBot
{
	private Map<IGuild, String> prefixMap;
	private Map<IGuild, IRole> memberRoleMap;
	private ExecutorService pool;
	
	public GuildBot(String token)
	{
		 new ClientBuilder()
				.withToken(token)
				.registerListeners(this)
				.login();
		
		prefixMap = new HashMap<>();
		pool = Executors.newCachedThreadPool();
	}
	
	@EventSubscriber
	public void onMessageReceived(MessageReceivedEvent e)
	{
		pool.execute(() -> process(e));
	}
	
	private void process(MessageReceivedEvent e)
	{
		IGuild theGuild = e.getGuild();
		String prefix = prefixMap.get(theGuild);
		if (prefix == null)
		{
			requestPrefix(theGuild);
			return;
		}
		
		// I know this is a cringy command interpreter but the bot only needs like two commadns
		String content = e.getMessage().getContent();
		if (!content.startsWith(prefix))
		{
			return;
		}
		
		String[] sliced = content.substring(prefix.length()).split(" ");
		String[] args = Arrays.copyOf(sliced, sliced.length-1);
		switch(sliced[0].toLowerCase())
		{
			case "register": register(e.getGuild(), e.getAuthor(), args); break;
			case "setrole": setRole(e.getGuild(), e.getAuthor(), args); break;
			default: return;
		}
		
		e.getMessage().delete();
	}
	
	private void register(IGuild g, IUser author, String[] args)
	{
		String ign = args[0];
		g.setUserNickname(author, ign);
		IRole r = memberRoleMap.get(g);
		if (r != null)
		{
			author.addRole(r);
		}
	}
	
	private void setRole(IGuild g, IUser author, String[] args)
	{
		if (!g.getOwner().equals(author))
		{
			return;
		}
		
		IRole r = g.getRolesByName(args[0]).get(0);
		memberRoleMap.put(g, r);
	}

	private void requestPrefix(IGuild guild)
	{
		IPrivateChannel pmWithOwner = guild.getOwner().getOrCreatePMChannel();
		pmWithOwner.sendMessage("Hey guild owner! Please set a prefix to use! Something like `!` or `?`, but you could use anything, really. Keep in mind that popular prefixes (especially `!`) might be incompatible with other bots.");
		IMessage response = Requester.waitForMessage(pmWithOwner);
		pmWithOwner.sendMessage("Set prefix: `".concat(response.getContent()).concat("`"));
		prefixMap.put(guild, response.getContent());
	}

}
