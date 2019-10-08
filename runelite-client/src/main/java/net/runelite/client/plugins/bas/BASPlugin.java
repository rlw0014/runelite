/*
 * Copyright (c) 2019, Jacob M <https://github.com/jacoblairm>
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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ObjectArrays;
import java.awt.event.KeyEvent;
import java.io.IOException;
import net.runelite.api.widgets.WidgetID;
import net.runelite.client.eventbus.Subscribe;
import com.google.inject.Provides;
import java.io.BufferedReader;
import java.io.StringReader;
import net.runelite.client.input.KeyListener;
import net.runelite.client.input.KeyManager;
import net.runelite.client.util.Text;
import net.runelite.http.api.RuneLiteAPI;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.RequestBody;
import okhttp3.MultipartBody;
import java.util.ArrayList;
import java.util.List;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.ClanMemberJoined;
import net.runelite.api.events.ClanMemberLeft;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.MenuAction;
import net.runelite.api.MenuEntry;
import net.runelite.api.events.MenuEntryAdded;
import net.runelite.api.events.PlayerMenuOptionClicked;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetInfo;
import net.runelite.client.chat.ChatColorType;
import net.runelite.client.chat.ChatMessageBuilder;
import net.runelite.client.chat.ChatMessageManager;
import net.runelite.client.chat.QueuedMessage;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.util.Text;
import net.runelite.api.ClanMember;
import org.apache.commons.lang3.ArrayUtils;

@Slf4j
@PluginDescriptor(
		name = "BAS",
		description = "BAS Customer CC Info",
		tags = {"minigame"}
)
public class BASPlugin extends Plugin implements KeyListener
{
	private static String ccName = "Ba Services"; //make sure space ascii is correct
	private static final String KICK_OPTION = "Kick";
	private static final String MARK_DONE = "Mark Done";
	private static final String MARK_INPROGRESS = "In-Progress";
	private static final String MARK_NOTINPROGRESS = "Mark Online";
	private static final String BUY_TORSO_REG = "Reg Torso";
	private static final String BUY_TORSO_PREM = "Prem Torso";
	private static final String BUY_LVL5_REG = "Reg Lvl 5s";
	private static final String BUY_LVL5_PREM = "Prem Lvl 5s";
	private static final String BUY_QK_REG = "Reg Queen Kill";
	private static final String BUY_QK_PREM = "Prem Queen Kill";
	private static final String BUY_HAT_REG = "Reg Hat";
	private static final String BUY_HAT_PREM = "Prem Hat";
	private static final String BUY_1R_REG = "Reg 1R Points";
	private static final String BUY_1R_PREM = "Prem 1R Points";
	private static final int clanSetupWidgetID = 24;
	private static final ImmutableList<String> BAS_OPTIONS = ImmutableList.of(MARK_DONE, MARK_INPROGRESS, MARK_NOTINPROGRESS);
	private static final ImmutableList<String> BAS_BUY_OPTIONS = ImmutableList.of(BUY_1R_PREM,BUY_1R_REG,BUY_HAT_PREM,BUY_HAT_REG,BUY_QK_PREM,BUY_QK_REG,BUY_LVL5_PREM
	,BUY_LVL5_REG,BUY_TORSO_PREM,BUY_TORSO_REG);
	private static int spreadsheetIgnoreLines = 4;
	private List<String[]> csvContent = new ArrayList<>();
	private List<String> ccMembersList = new ArrayList<>();
	private List<String> ccPremList = new ArrayList<>();
	private Widget[] membersWidgets = new Widget[0];
	private int lastCheckTick;
	private int ccCount;
	private static final ImmutableList<String> AFTER_OPTIONS = ImmutableList.of("Message", "Add ignore", "Remove friend", KICK_OPTION);
	private boolean shiftDown;
	private boolean isUpdated = false;

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

	@Inject
	private KeyManager keyManager;

	@Provides
	BASConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(BASConfig.class);
	}

	@Override
	protected void startUp() throws Exception
	{
		keyManager.registerKeyListener(this);
		isUpdated = updatedClient();
	}

	@Override
	protected void shutDown() throws Exception
	{
		keyManager.unregisterKeyListener(this);
		ccUpdate();
		ccPremList.clear();
		csvContent.clear();
	}

	@Subscribe
	public void onGameTick(GameTick event)
	{
		if(!isUpdated || !isRank())
		{
			return;
		}

		updateCCPanel();
		if(client.getClanChatCount()>0 && ccCount!=client.getClanChatCount())
		{
			ccUpdate();
			ccCount=client.getClanChatCount();
		}
		if(config.getNextCustomer())
		{
			Widget clanSetupWidget = client.getWidget(WidgetID.CLAN_CHAT_GROUP_ID, clanSetupWidgetID);
			if(clanSetupWidget!=null)
			{
				clanSetupWidget.setText("Next Customer");
				clanSetupWidget.setHasListener(false);
			}
		}
	}

    @Subscribe
    public void onClanMemberJoined(ClanMemberJoined event)
    {
		ccUpdate();
    }

    @Subscribe
    public void onClanMemberLeft(ClanMemberLeft event)
    {
		ccUpdate();
    }

	@Subscribe
	public void onMenuEntryAdded(MenuEntryAdded event)
	{
		if(!isRank() || !isUpdated)
		{
			return;
		}

		int groupId = WidgetInfo.TO_GROUP(event.getActionParam1());
		String option = event.getOption();

		if (groupId == WidgetInfo.CLAN_CHAT.getGroupId() ||
				groupId == WidgetInfo.CHATBOX.getGroupId() && !KICK_OPTION.equals(option)//prevent from adding for Kick option (interferes with the raiding party one)
				)
		{
			if(config.getNextCustomer() && groupId == WidgetInfo.CLAN_CHAT.getGroupId() && WidgetInfo.TO_CHILD(event.getActionParam1())==clanSetupWidgetID && client.getClanOwner().equals(ccName))
			{
				MenuEntry newMenu = client.getMenuEntries()[1];
				newMenu.setOption("Next-customer");
				newMenu.setParam0(0);
				newMenu.setParam1(0);
				insertMenuEntry(newMenu, client.getMenuEntries(), false);
			}

			if (!AFTER_OPTIONS.contains(option))
			{
				return;
			}

			if(config.markCustomerOptions()&& ccMembersList.contains(Text.removeTags(Text.sanitize(event.getTarget()))))
			{
				for (String basOption : BAS_OPTIONS)
				{
					final MenuEntry menuOption = new MenuEntry();
					menuOption.setOption(basOption);
					menuOption.setTarget(event.getTarget());
					menuOption.setType(MenuAction.RUNELITE.getId());
					menuOption.setParam0(event.getActionParam0());
					menuOption.setParam1(event.getActionParam1());
					menuOption.setIdentifier(event.getIdentifier());

					insertMenuEntry(menuOption, client.getMenuEntries(), true);
				}
			}
			else if(shiftDown && config.addToQueue())
			{
				for (String basOption : BAS_BUY_OPTIONS)
				{
					if(
							((basOption.equals(BUY_TORSO_REG)||basOption.equals(BUY_TORSO_PREM))&&config.torsoOptions()) ||
							((basOption.equals(BUY_HAT_REG)||basOption.equals(BUY_HAT_PREM))&&config.hatOptions()) ||
							((basOption.equals(BUY_QK_REG)||basOption.equals(BUY_QK_PREM))&&config.qkOptions()) ||
							((basOption.equals(BUY_1R_REG)||basOption.equals(BUY_1R_PREM))&&config.OneROptions()) ||
							((basOption.equals(BUY_LVL5_REG)||basOption.equals(BUY_LVL5_PREM))&&config.Lvl5Options())
					)
					{
						final MenuEntry menuOption = new MenuEntry();
						menuOption.setOption(basOption);
						menuOption.setTarget(event.getTarget());
						menuOption.setType(MenuAction.RUNELITE.getId());
						menuOption.setParam0(event.getActionParam0());
						menuOption.setParam1(event.getActionParam1());
						menuOption.setIdentifier(event.getIdentifier());

						insertMenuEntry(menuOption, client.getMenuEntries(), true);
					}
				}
			}
		}
	}

	@Subscribe
	public void onPlayerMenuOptionClicked(PlayerMenuOptionClicked event)
	{

		if(BAS_BUY_OPTIONS.contains(event.getMenuOption()))
		{
			addCustomerToQueue(event.getMenuTarget(), event.getMenuOption());
		}

		if(!BAS_OPTIONS.contains(event.getMenuOption()))
		{
			return;
		}

		String appendMessage = "";

		switch(event.getMenuOption())
		{
			case MARK_INPROGRESS:
				appendMessage = "in progress.";
				markCustomer(1, event.getMenuTarget());
				break;
			case MARK_DONE:
				appendMessage = "done.";
				markCustomer(2, event.getMenuTarget());
				break;
			case MARK_NOTINPROGRESS:
				appendMessage = "online.";
				markCustomer(3, event.getMenuTarget());
				break;
		}

		final String chatMessage = new ChatMessageBuilder()
				.append(ChatColorType.NORMAL)
				.append("Marked " + event.getMenuTarget() + " as ")
				.append(ChatColorType.HIGHLIGHT)
				.append(appendMessage)
				.build();

			chatMessageManager.queue(QueuedMessage.builder()
					.type(ChatMessageType.CONSOLE)
					.runeLiteFormattedMessage(chatMessage)
					.build());

	}

	@Subscribe
	public void onMenuOptionClicked(MenuOptionClicked event)
	{
		if(event.getMenuOption().equals("Next-customer"))
		{
			getNextCustomer();
		}
	}

	private boolean updatedClient() throws IOException
	{
		boolean isUpdated = false;
		HttpUrl url = RuneLiteAPI.getApiBase().newBuilder()
				.addPathSegment("feed.js")
				.build();

		Request request = new Request.Builder()
				.url(url)
				.build();

		try (Response response = RuneLiteAPI.CLIENT.newCall(request).execute())
		{
			log.info(request+ " "+response.isSuccessful());
			if (response.isSuccessful())
			{
				isUpdated = true;
			}
		}
		return isUpdated;
	}

	private boolean isRank()
	{
		if(client.getLocalPlayer().getName()==null || client.getClanChatCount()<1 || !client.getClanOwner().equals(ccName) || !isUpdated)
		{
			return false;
		}

		boolean isRank = false;

		for(ClanMember member : client.getClanMembers())
		{
			if(Text.sanitize(client.getLocalPlayer().getName()).equals(Text.sanitize(member.getUsername())) && member.getRank().getValue()>=0)
			{
				isRank = true;
			}
		}
		return isRank;
	}

	private void getNextCustomer()
	{
		OkHttpClient httpClient = RuneLiteAPI.CLIENT;

		HttpUrl httpUrl = new HttpUrl.Builder()
				.scheme("http")
				.host("blairm.net")
				.addPathSegment("bas")
				.addPathSegment("update.php")
				.addQueryParameter("n", "1")
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

			}

			@Override
			public void onResponse(Call call, Response response) throws IOException
			{
				BufferedReader in = new BufferedReader(new StringReader(response.body().string()));
				String s;
				String CustId = "";
				while ((s = in.readLine()) != null)
				{
					CustId = s;
				}
				final String chatMessage = new ChatMessageBuilder()
						.append(ChatColorType.NORMAL)
						.append("Next customer in line: ")
						.append(ChatColorType.HIGHLIGHT)
						.append(CustId)
						.build();

				chatMessageManager.queue(QueuedMessage.builder()
						.type(ChatMessageType.CONSOLE)
						.runeLiteFormattedMessage(chatMessage)
						.build());

			}
		});
	}

	private void addCustomerToQueue(String name, String item)
	{

		String queueName = config.queueName().equals("") ? client.getLocalPlayer().getName() : config.queueName();
		String formItem = "";
		String priority = "Regular";

		switch(item)
		{
			case BUY_HAT_REG:
				formItem = "Hat";
				break;
			case BUY_LVL5_REG:
				formItem = "Level 5 Roles";
				break;
			case BUY_QK_REG:
				formItem = "Queen Kill - Diary";
				break;
			case BUY_TORSO_REG:
				formItem = "Torso";
				break;
			case BUY_1R_REG:
				formItem = "One Round - Points";
				break;
			case BUY_HAT_PREM:
				priority = "Premium";
				formItem = "Hat";
				break;
			case BUY_LVL5_PREM:
				priority = "Premium";
				formItem = "Level 5 Roles";
				break;
			case BUY_QK_PREM:
				priority = "Premium";
				formItem = "Queen Kill - Diary";
				break;
			case BUY_TORSO_PREM:
				priority = "Premium";
				formItem = "Torso";
				break;
			case BUY_1R_PREM:
				priority = "Premium";
				formItem = "One Round - Points";
				break;
		}

		HttpUrl httpUrl = new HttpUrl.Builder()
				.scheme("https")
				.host("docs.google.com")
				.addPathSegment("forms")
				.addPathSegment("d")
				.addPathSegment("e")
				.addPathSegment("1FAIpQLSc06_IrTbleP0uZBiOt1yMcI5kvOrvkzgaVLLmEDRLqJSSoVg")
				.addPathSegment("formResponse")
				.build();

		RequestBody requestBody = new MultipartBody.Builder()
				.setType(MultipartBody.FORM)
				.addFormDataPart("entry.1481518570", priority)
				.addFormDataPart("entry.1794472797", name.replace('\u00A0', ' '))
				.addFormDataPart("entry.1391010025", formItem)
				.addFormDataPart("entry.1284888696", queueName)
				.build();

		Request request = new Request.Builder()
				.url(httpUrl)
				.post(requestBody)
				.build();

		RuneLiteAPI.CLIENT.newCall(request).enqueue(new Callback()
		{
			@Override
			public void onFailure(Call call, IOException e)
			{
				log.info("failed customer to queue");
			}

			@Override
			public void onResponse(Call call, Response response)
			{
				response.close();
				log.info("added customer to queue");

				OkHttpClient httpClient = RuneLiteAPI.CLIENT;

				HttpUrl httpUrl = new HttpUrl.Builder()
						.scheme("http")
						.host("blairm.net")
						.addPathSegment("bas")
						.addPathSegment("update.php")
						.addQueryParameter("a", name.replace('\u00A0', ' '))
						.build();

				Request request = new Request.Builder()
						.header("User-Agent", "RuneLite")
						.url(httpUrl)
						.build();

				final String chatMessage = new ChatMessageBuilder()
						.append(ChatColorType.NORMAL)
						.append("Sent a request to add " + name + " for "+item+".")
						.build();

				chatMessageManager.queue(QueuedMessage.builder()
						.type(ChatMessageType.CONSOLE)
						.runeLiteFormattedMessage(chatMessage)
						.build());

				httpClient.newCall(request).enqueue(new Callback()
				{
					@Override
					public void onFailure(Call call, IOException e)
					{

					}

					@Override
					public void onResponse(Call call, Response response) throws IOException
					{
						BufferedReader in = new BufferedReader(new StringReader(response.body().string()));
						String s;
						String CustId = "";
						while ((s = in.readLine()) != null)
						{
							CustId = s;
						}
						final String chatMessage = new ChatMessageBuilder()
								.append(ChatColorType.NORMAL)
								.append("Added '" + name + "' to the queue successfully, Their ID is ")
								.append(ChatColorType.HIGHLIGHT)
								.append(CustId)
								.build();

						chatMessageManager.queue(QueuedMessage.builder()
								.type(ChatMessageType.CONSOLE)
								.runeLiteFormattedMessage(chatMessage)
								.build());

					}
				});
			}
		});

	}

	private void insertMenuEntry(MenuEntry newEntry, MenuEntry[] entries, boolean swap)
	{
		MenuEntry[] newMenu = ObjectArrays.concat(entries, newEntry);
		int menuEntryCount = newMenu.length;
		if(swap)
		{
			ArrayUtils.swap(newMenu, menuEntryCount - 1, menuEntryCount - 2);
		}
		client.setMenuEntries(newMenu);
	}

    private void ccUpdate()
	{
		if(lastCheckTick==client.getTickCount() || !isRank() || !isUpdated)
		{
			return;
		}
		readCSV();
		checkUsers();
		updateQueue();
		lastCheckTick=client.getTickCount();
	}

    private void checkUsers()
	{
		for (ClanMember memberCM : client.getClanMembers())
		{
			String member = memberCM.getUsername();

			for (String[] user : csvContent)
			{
				if (user[1].toLowerCase().contains(member.toLowerCase()))
				{
					if(user[0].equals("P"))
					{
						if(!ccPremList.contains(member))
						{
							ccPremList.add(member);
							final String chatMessage = new ChatMessageBuilder()
									.append(ChatColorType.NORMAL)
									.append("Premium leech " + member)
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
					}
				}
			}
		}

		for (String premMember : ccPremList)
		{
			boolean isOnline = false;
			for (ClanMember memberCM : client.getClanMembers())
			{
				String member = memberCM.getUsername();
				if (premMember.equals(member))
				{
					isOnline = true;
				}
			}
			if (!isOnline)
			{
				ccPremList.remove(premMember);
				final String chatMessage = new ChatMessageBuilder()
						.append(ChatColorType.NORMAL)
						.append("Premium leech " + premMember)
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

    private void updateCCPanel()
	{
		Widget clanChatWidget = client.getWidget(WidgetInfo.CLAN_CHAT);

		if (clanChatWidget != null && !clanChatWidget.isHidden())
		{
			Widget clanChatList = client.getWidget(WidgetInfo.CLAN_CHAT_LIST);
			Widget owner = client.getWidget(WidgetInfo.CLAN_CHAT_OWNER);
			if (client.getClanChatCount() > 0 && owner.getText().equals("<col=ffffff>Ba Services</col>"))
			{
				membersWidgets = clanChatList.getDynamicChildren();
				for (Widget member : membersWidgets)
				{
					if (member.getTextColor() == 16777215) {
						int lineNum = 1;
						for (String[] user : csvContent)
						{
							if(lineNum++>=spreadsheetIgnoreLines)
							{
								if (user[1].toLowerCase().contains(member.getText().toLowerCase()))
								{
									if(!ccMembersList.contains(member.getText()))
									{
										ccMembersList.add(member.getText());
									}

									switch (user[2])
									{
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
									if (user[0].equals("P"))
									{
										member.setTextColor(6604900);
									} else
									{
										member.setTextColor(6579400);
									}
								}
							}
						}
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
				int lineNum = 0;
				csvContent.clear();
				while ((s = in.readLine()) != null)
				{
					String[] splitString = s.split(",");
					if(splitString.length>5)
					{
						csvContent.add(new String[]{splitString[2], splitString[2].equals("R") ? splitString[4] : splitString[3], splitString[0]});
					}
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

		String csv = "";
		for (ClanMember member : client.getClanMembers())
		{
			String memberName = member.getUsername();
			if (csv.equals(""))
			{
				csv = memberName+"#"+member.getRank().getValue();
			}
			else
			{
				csv = csv + "," + memberName+"#"+member.getRank().getValue();
			}
		}
		if (csv.equals(""))
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
				.addQueryParameter("n", Text.sanitize(client.getLocalPlayer().getName()))
				.build();

		Request request = new Request.Builder()
				.header("User-Agent", "RuneLite")
				.url(httpUrl)
				.build();

		log.info("sending: " + httpUrl.toString());

		httpClient.newCall(request).enqueue(new Callback()
		{
			@Override
			public void onFailure(Call call, IOException e)
			{
				log.warn("Error sending http request.", e.getMessage());
			}

			@Override
			public void onResponse(Call call, Response response) throws IOException { }
		});
	}

	private void markCustomer(int option, String name)
	{
		OkHttpClient httpClient = RuneLiteAPI.CLIENT;
		HttpUrl httpUrl = new HttpUrl.Builder()
				.scheme("http")
				.host("blairm.net")
				.addPathSegment("bas")
				.addPathSegment("update.php")
				.addQueryParameter("o", option+"")
				.addQueryParameter("n", name)
				.build();

		Request request = new Request.Builder()
				.header("User-Agent", "RuneLite")
				.url(httpUrl)
				.build();

		log.info("marking: " + httpUrl.toString());

		httpClient.newCall(request).enqueue(new Callback()
		{
			@Override
			public void onFailure(Call call, IOException e)
			{
				log.warn("Error sending http request.", e.getMessage());
			}

			@Override
			public void onResponse(Call call, Response response) throws IOException { }
		});
	}

	@Subscribe
	public void onChatMessage(ChatMessage chatMessage)
	{
		if(!isRank() || !isUpdated || chatMessage.getType()!=ChatMessageType.FRIENDSCHAT)
		{
			return;
		}

		OkHttpClient httpClient = RuneLiteAPI.CLIENT;
		HttpUrl httpUrl = new HttpUrl.Builder()
				.scheme("http")
				.host("blairm.net")
				.addPathSegment("bas")
				.addPathSegment("update.php")
				.addQueryParameter("c", chatMessage.getMessage())
				.addQueryParameter("m", Text.removeTags(chatMessage.getName()))
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
			public void onResponse(Call call, Response response) throws IOException { }
		});
	}

	@Override
	public void keyTyped(KeyEvent e)
	{
	}

	@Override
	public void keyPressed(KeyEvent e)
	{
		if (e.getKeyCode() == KeyEvent.VK_SHIFT)
		{
			shiftDown = true;
		}

	}

	@Override
	public void keyReleased(KeyEvent e)
	{
		if (e.getKeyCode() == KeyEvent.VK_SHIFT)
		{
			shiftDown = false;
		}
	}
}