/*
 * Copyright (c) 2018, Ryan <https://github.com/rlw0014>
 * Copyright (c) 2018, Jacob M <https://github.com/jacoblairm>
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package net.runelite.client.plugins.cba;

import com.google.inject.Provides;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Scanner;
import java.util.TreeSet;
import javax.inject.Inject;
import net.runelite.api.ChatMessageType;
import net.runelite.api.ClanMember;
import net.runelite.api.Client;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.ClanMemberJoined;
import net.runelite.api.events.ClanMemberLeft;
import net.runelite.api.events.GameTick;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.util.Text;

@ConfigGroup("CBA")
@PluginDescriptor(
		name = "CBA",
		description = "CBA Ranks Activity",
		tags = {"minigame"}
)
public class CBAPlugin extends Plugin
{
	private static String ccName = "Casual Ba"; //make sure space ascii is correct
	private TreeSet<Player> currentMembers;
	private TreeSet<Player> membersOfDay;
	private TreeSet<Player> activityList;
	List<String> activityChecks = Arrays.asList(
		   "+?", "f+", "0", "1", "2", "3", "4",
		   "+", "f+?", "+0", "+1", "+2", "+3", "+4",
		   "f+0", "f+1", "f+2", "f+3", "f+4"
	);
	private int ccCount;

	@Inject
	private Client client;

	@Inject
	private ConfigManager configManager;

	@Inject
	private OverlayManager overlayManager;

	@Inject
	private CBAConfig config;

	@Provides
	CBAConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(CBAConfig.class);
	}

	@Override
	protected void startUp()
	{
		currentMembers = new TreeSet<>();
		membersOfDay = new TreeSet<>();
		activityList = new TreeSet<>();
	}

	@Override
	protected void shutDown() throws Exception
	{
		try {
			ccUpdate();
			exportData();
			currentMembers = null;
			membersOfDay = null;
			activityList = null;
		} catch (Throwable e) {
			e.printStackTrace();
		}

	}

	@Subscribe
	public void onGameTick(GameTick event) throws IOException
	{
		if (!client.getClanOwner().equals(ccName))
		{
			return;
		}
		if (ccCount != client.getClanChatCount())
		{
			ccUpdate();
			ccCount = client.getClanChatCount();
		}
	}

	@Subscribe
	public void onClanMemberJoined(ClanMemberJoined clanMemberJoined) throws IOException
	{
		ccUpdate();
    	}

    	@Subscribe
	public void onClanMemberLeft(ClanMemberLeft clanMemberLeft) throws IOException
	{
		ccUpdate();
	}

	@Subscribe
	public void onChatMessage(ChatMessage chatMessage) throws IOException
	{
		if (!chatMessage.getType().equals(ChatMessageType.FRIENDSCHAT))
		{
			return;
		}
		if (!client.getClanOwner().equals(ccName))
		{
			return;
		}
		String message = chatMessage.getMessage().toLowerCase();
		for(String activity: activityChecks)
		{
			if (message.toLowerCase().startsWith(activity))
			{
				System.out.println("Adding " + chatMessage.getName() + " to Activity List");
				activityList.add(new Player(Text.removeTags(chatMessage.getName()), true));
			}
		}
		ccUpdate();
	}

	private void ccUpdate() throws IOException
	{
		if (!client.getClanOwner().equals(ccName))
		{
			return;
		}
		System.out.println("Updating CC");
		for (ClanMember member: client.getClanMembers())
		{
			if(checkIfRank(member))
			{
				Player p1 = new Player(member.getUsername(), false);
				if (search(p1, currentMembers) == -1 || currentMembers.isEmpty())
				{
					currentMembers.add(p1);
				}
			}
		}
		exportData();
	}

	private boolean checkIfRank(ClanMember member)
	{
		return member.getRank().getValue() >= 0;
	}

	private void exportData() throws IOException
	{
		DateFormat dateFormat = new SimpleDateFormat("MM/dd/YYYY");
		Date date = new Date();
		String todaysDate = dateFormat.format(date);
		todaysDate = todaysDate.replaceAll("\\/", "");

		String ranksOnlineFileName = config.dataLocation() + "\\RanksOnline\\" + todaysDate + ".txt";
		String ranksActivityFileName = config.dataLocation() + "\\RanksActivity\\" + todaysDate + ".txt";

		membersOfDay.addAll(currentMembers);

		if (config.dataLocation() != null)
		{
			readFromTxt(ranksOnlineFileName, membersOfDay);
			readFromTxt(ranksActivityFileName, activityList);
			writeToTxt(ranksOnlineFileName, membersOfDay);
			writeToTxt(ranksActivityFileName, activityList);
		}

		System.out.println("currentMembers List: " + currentMembers);
		System.out.println("membersOfDay List: " + membersOfDay);
		System.out.println("Activity List: " + activityList);
	}

	private void readFromTxt(String fileName, TreeSet<Player> players) throws IOException
	{
		System.out.println("fileName: " + fileName);
		File file = new File(fileName);
		file.getParentFile().mkdirs();
		file.createNewFile();
		Scanner inFile = new Scanner(file);
		while (inFile.hasNext())
		{
			players.add(new Player(inFile.nextLine(), false));
		}
	}

	private void writeToTxt(String fileName, TreeSet<Player> players) throws IOException
	{
		File file = new File(fileName);
		FileWriter fileWriter = new FileWriter(file);
		PrintWriter printWriter = new PrintWriter(fileWriter);
		for (Player player: players)
		{
			printWriter.write(player.getName());
			printWriter.write(System.lineSeparator());
		}
		fileWriter.close();
	}

	public int search(Player player, TreeSet<Player> set)
	{
		int index = 0;
		for (Player p: set)
		{
			if (player.getName().equals(p.getName()))
			{
				return index;
			}
			else
			{
				index++;
			}
		}
		return -1;
	}
}