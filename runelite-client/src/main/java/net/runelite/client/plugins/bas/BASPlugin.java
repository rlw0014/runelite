/*
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
package net.runelite.client.plugins.bas;

import java.io.IOException;

import net.runelite.api.events.ClanMemberJoined;
import net.runelite.client.eventbus.Subscribe;
import com.google.inject.Provides;
import java.io.BufferedReader;
import java.io.StringReader;
import net.runelite.http.api.RuneLiteAPI;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import java.util.ArrayList;
import java.util.List;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetInfo;
import net.runelite.client.chat.ChatColorType;
import net.runelite.api.events.GameTick;
import net.runelite.client.chat.ChatMessageBuilder;
import net.runelite.client.chat.ChatMessageManager;
import net.runelite.client.chat.QueuedMessage;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.overlay.OverlayManager;


@Slf4j
@PluginDescriptor(
		name = "BAS",
		description = "BAS Customer CC Info",
		tags = {"minigame"}
)
public class BASPlugin extends Plugin
{
	private List<String[]> csvContent = new ArrayList<>();
	private List<String> premList = new ArrayList<>();
	private Widget[] members = new Widget[0];
	private int count;

	@Inject
	private Client client;

	@Inject
	private ConfigManager configManager;

	@Inject
	private ChatMessageManager chatMessageManager;

	@Inject
	private OverlayManager overlayManager;

	@Inject
	private BASConfig config;

	@Provides
	BASConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(BASConfig.class);
	}

	@Override
	protected void startUp() throws Exception
	{
		readCSV();
		count=0;
	}

	@Override
	protected void shutDown()
	{
		count=0;
	}

	@Subscribe
	public void onGameTick(GameTick event)
	{
		if(count!=client.getClanChatCount())
		{
			log.info("count: ");
			readCSV();
			updateQueue();
		}
		count=members.length==0?0:client.getClanChatCount();
		checkCustomers();
	}

	private void checkCustomers()
	{
		Widget clanChatTitleWidget = client.getWidget(WidgetInfo.CLAN_CHAT_TITLE);
		if (clanChatTitleWidget != null)
		{
			Widget clanChatList = client.getWidget(WidgetInfo.CLAN_CHAT_LIST);
			Widget owner = client.getWidget(WidgetInfo.CLAN_CHAT_OWNER);
			if (client.getClanChatCount() > 0 && owner.getText().equals("<col=ffffff>Ba Services</col>"))
			{
				members = clanChatList.getDynamicChildren();
				for (Widget member : members)
				{
					if (member.getTextColor() == 16777215) {
						for (String[] user : csvContent) {
							if (user[1].toLowerCase().contains(member.getText().toLowerCase())) {
								switch (user[2]) {
									case "":
										member.setText(member.getText() + " (U)");
										break;
									case "Online":
										member.setText(member.getText() + " (O)");
										break;
									case "In Progress":
										member.setText(member.getText() + " (P)");
										break;
								}
								if (user[0].equals("P")) {
									member.setTextColor(6604900);
									boolean inList = false;
									for (String prem : premList) {
										if (member.getText().toLowerCase().contains(prem.toLowerCase())) {
											inList = true;
										}
									}
									if (!inList) {
										premList.add(member.getText());
										final String chatMessage = new ChatMessageBuilder()
												.append(ChatColorType.NORMAL)
												.append("Premium leech " + member.getText())
												.append(ChatColorType.HIGHLIGHT)
												.append(" online.")
												.build();
										if(config.premNotifier())
										{
											chatMessageManager.queue(QueuedMessage.builder()
													.type(ChatMessageType.CONSOLE)
													.runeLiteFormattedMessage(chatMessage)
													.build());
										}
									}
								} else {
									member.setTextColor(6579400);
								}
							}
						}
					}
				}
				if(premList.size()>0)
				{
					for (String prem : premList) {
						boolean online = false;
						for (Widget member : members) {
							if (member.getText().toLowerCase().contains(prem.toLowerCase())) {
								online = true;
							}
						}
						if (!online) {
							premList.remove(prem);
							final String chatMessage = new ChatMessageBuilder()
									.append(ChatColorType.NORMAL)
									.append("Premium leech " + prem)
									.append(ChatColorType.HIGHLIGHT)
									.append(" offline.")
									.build();
							if(config.premNotifier())
							{
							chatMessageManager.queue(QueuedMessage.builder()
									.type(ChatMessageType.CONSOLE)
									.runeLiteFormattedMessage(chatMessage)
									.build());
							}
						}
					}
				}
			}
		}
	}

	private void checkPrem()
	{
		if (premList.size() > 0)
		{
			for (String prem : premList)
			{
				boolean online = false;
				for (Widget member : members)
				{
					if (member.getText().toLowerCase().contains(prem.toLowerCase()))
					{
						online = true;
					}
				}
				if (!online)
				{
					premList.remove(prem);
					final String chatMessage = new ChatMessageBuilder()
							.append(ChatColorType.NORMAL)
							.append("Premium leech " + prem)
							.append(ChatColorType.HIGHLIGHT)
							.append(" offline.")
							.build();
					if (config.premNotifier())
					{
						chatMessageManager.queue(QueuedMessage.builder()
								.type(ChatMessageType.CONSOLE)
								.runeLiteFormattedMessage(chatMessage)
								.build());
					}
				}
			}
		}
	}

	private void readCSV()
	{
		OkHttpClient httpClient = RuneLiteAPI.CLIENT;

		HttpUrl httpUrl = new HttpUrl.Builder()
				.scheme("https")
				.host("docs.google.com")
				.addPathSegment("spreadsheets")
				.addPathSegment("d")
				.addPathSegment("1Jh9Nj6BvWVgzZ9urnTTNniQLkgprx_TMggaz8gt_iDM")
				.addPathSegment("export")
				.addQueryParameter("format", "csv")
				.build();

		Request request = new Request.Builder()
				.header("User-Agent", "RuneLite")
				.url(httpUrl)
				.build();


		httpClient.newCall(request).enqueue(new Callback()
		{
			@Override
			public void onFailure(Call call, IOException e)
			{
				log.warn("Error sending http request.", e.getMessage());
			}

			@Override
			public void onResponse(Call call, Response response) throws IOException
			{
				BufferedReader in = new BufferedReader(new StringReader(response.body().string()));
				String s;
				csvContent.clear();
				while ((s = in.readLine()) != null)
				{
					String[] splitString = s.split(",");
					csvContent.add(new String[]{splitString[2], splitString[2].equals("R") ? splitString[4] : splitString[3], splitString[0]});
				}
			}
		});
	}

	private void updateQueue()
	{
		if(!config.autoUpdateQueue())
		{
			return;
		}

		Widget clanChatTitleWidget = client.getWidget(WidgetInfo.CLAN_CHAT_TITLE);
		if (clanChatTitleWidget != null) {
			Widget clanChatList = client.getWidget(WidgetInfo.CLAN_CHAT_LIST);
			Widget owner = client.getWidget(WidgetInfo.CLAN_CHAT_OWNER);
			if (client.getClanChatCount() > 0 && owner.getText().equals("<col=ffffff>Ba Services</col>"))
			{
					String csv = "";

					for (Widget member : members)
					{
						if (member.getTextColor() != 16777060 && member.getTextColor()!=0 && member.getTextColor()!=901389)
						{
							String memberName = member.getText();

							if(memberName.contains("("))
							{
								memberName = memberName.split(" \\(")[0];
							}
							if(csv.equals(""))
							{
								csv = memberName;
							}
							else
							{
								csv = csv + "," + memberName;
							}
						}
					}
					if(csv.equals(""))
					{
						return;
					}

					OkHttpClient httpClient = RuneLiteAPI.CLIENT;
					HttpUrl httpUrl = new HttpUrl.Builder()
							.scheme("http")
							.host("blairm.net")
							.addPathSegment("bas")
							.addPathSegment("update.php")
							.addQueryParameter("d", csv)
							.build();

					Request request = new Request.Builder()
							.header("User-Agent", "RuneLite")
							.url(httpUrl)
							.build();

					log.info("sending: " +httpUrl.toString());

					httpClient.newCall(request).enqueue(new Callback()
					{
						@Override
						public void onFailure(Call call, IOException e)
						{
							log.warn("Error sending http request.", e.getMessage());
						}

						@Override
						public void onResponse(Call call, Response response) throws IOException
						{
						}
					});


			}
		}
	}
}