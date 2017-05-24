package quinnthusiast.discord.guild_bot;

import sx.blah.discord.api.events.EventSubscriber;
import sx.blah.discord.handle.impl.events.guild.channel.message.MessageReceivedEvent;
import sx.blah.discord.handle.obj.IChannel;
import sx.blah.discord.handle.obj.IMessage;

public class Requester
{
	
	private static final Object MONITOR_WAITFORMESSAGE = new Object();
	private static IChannel channel_waitForMessage;
	private static IMessage o_waitForMessage;
	private static boolean lock_waitForMessage;
	public static IMessage waitForMessage(IChannel channel)
	{
		// reset
		lock_waitForMessage = true;
		o_waitForMessage = null;
		channel_waitForMessage = channel; 
		
		channel.getClient().getDispatcher().registerListener(e -> {
			if (e instanceof MessageReceivedEvent)
			{
				waitForMessageListener((MessageReceivedEvent) e);
			}
		});
		synchronized(MONITOR_WAITFORMESSAGE)
		{
			while (lock_waitForMessage)
			{
				try { MONITOR_WAITFORMESSAGE.wait(); } catch (InterruptedException e) {}
			}
		}
		
		return o_waitForMessage;
	}
	
	@EventSubscriber
	private static void waitForMessageListener(MessageReceivedEvent e)
	{
		if (!e.getChannel().equals(channel_waitForMessage)) { return; }
		synchronized (MONITOR_WAITFORMESSAGE)
		{
			o_waitForMessage = e.getMessage();
			// wake up thread
			lock_waitForMessage = false;
			MONITOR_WAITFORMESSAGE.notify();
		}
	}
}
