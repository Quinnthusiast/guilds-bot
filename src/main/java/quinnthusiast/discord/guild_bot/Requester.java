package quinnthusiast.discord.guild_bot;

import sx.blah.discord.api.events.EventSubscriber;
import sx.blah.discord.handle.impl.events.guild.channel.message.MessageReceivedEvent;
import sx.blah.discord.handle.obj.IChannel;
import sx.blah.discord.handle.obj.IMessage;

public class Requester
{
	
	private static IMessage o_waitForMessage;
	private static boolean lock_waitForMessage;
	public static IMessage waitForMessage(IChannel channel)
	{
		// reset
		lock_waitForMessage = true;
		o_waitForMessage = null;
		
		channel.getClient().getDispatcher().registerTemporaryListener(Requester::waitForMessageListener);
		synchronized(o_waitForMessage)
		{
			while (lock_waitForMessage)
			{
				try { o_waitForMessage.wait(); } catch (InterruptedException e) {}
			}
		}
		
		return o_waitForMessage;
	}
	
	@EventSubscriber
	private static void waitForMessageListener(MessageReceivedEvent e)
	{
		synchronized (o_waitForMessage)
		{
			o_waitForMessage = e.getMessage();
		}
		// wake up thread
		lock_waitForMessage = false;
		o_waitForMessage.notify();
	}
}
