/*
 * Copyright (c) 2018, Cameron <https://github.com/noremac201>
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
package net.runelite.client.plugins.batools;

import com.google.inject.Provides;
import java.awt.Color;
import java.awt.Font;
import java.awt.Image;
import java.awt.event.KeyEvent;
import java.awt.image.BufferedImage;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.inject.Inject;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Actor;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import static net.runelite.api.Constants.CHUNK_SIZE;
import net.runelite.api.ItemID;
import net.runelite.api.MenuEntry;
import net.runelite.api.MessageNode;
import net.runelite.api.NPC;
import net.runelite.api.NpcID;
import net.runelite.api.Player;
import net.runelite.api.Prayer;
import net.runelite.api.SoundEffectID;
import net.runelite.api.Tile;
import net.runelite.api.Varbits;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.ConfigChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.HitsplatApplied;
import net.runelite.api.events.InteractingChanged;
import net.runelite.api.events.ItemDespawned;
import net.runelite.api.events.ItemSpawned;
import net.runelite.api.events.MenuEntryAdded;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.api.events.NpcDespawned;
import net.runelite.api.events.NpcSpawned;
import net.runelite.api.events.VarbitChanged;
import net.runelite.api.events.WidgetHiddenChanged;
import net.runelite.api.events.WidgetLoaded;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetID;
import net.runelite.api.widgets.WidgetInfo;
import net.runelite.client.chat.ChatColorType;
import net.runelite.client.chat.ChatMessageBuilder;
import net.runelite.client.chat.ChatMessageManager;
import net.runelite.client.chat.QueuedMessage;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.game.ItemManager;
import net.runelite.client.input.KeyListener;
import net.runelite.client.input.KeyManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.ui.overlay.infobox.InfoBoxManager;
import net.runelite.client.util.ColorUtil;
import net.runelite.client.util.ImageUtil;
import net.runelite.client.util.Text;
import org.apache.commons.lang3.ArrayUtils;

@Slf4j
@PluginDescriptor(
	name = "BA Tools",
	description = "Custom tools for Barbarian Assault",
	tags = {"minigame", "overlay", "timer"}
)
public class BAToolsPlugin extends Plugin implements KeyListener
{
	@Getter
	private int collectedEggCount = 0;
	@Getter
	private int positiveEggCount = 0;
	@Getter
	private int wrongEggs = 0;
	@Getter
	private int HpHealed = 0;
	@Getter
	private int totalCollectedEggCount = 0;
	@Getter
	private int totalHpHealed = 0;
	private boolean hasAnnounced;
	private Font font;
	private final Image clockImage = ImageUtil.getResourceStreamFromClass(getClass(), "clock.png");
	private static final String ENDGAME_REWARD_NEEDLE_TEXT = "<br>5";
	private static final String START_WAVE = "1";
	private GameTimer gameTime;
	private Game game;
	private Wave wave;

	int inGameBit = 0;
	int tickNum;
	int pastCall = 0;
	private String currentWave = "1";
	private int lastHealer;
	private static final int BA_WAVE_NUM_INDEX = 2;
	private final List<MenuEntry> entries = new ArrayList<>();
	private HashMap<Integer, Instant> foodPressed = new HashMap<>();
	private CycleCounter counter;
	private Actor lastInteracted;
	private boolean shiftDown;
	private boolean ctrlDown;

	@Getter(AccessLevel.PACKAGE)
	private HashMap<WorldPoint, Integer> redEggs;

	@Getter(AccessLevel.PACKAGE)
	private HashMap<WorldPoint, Integer> greenEggs;

	@Getter(AccessLevel.PACKAGE)
	private HashMap<WorldPoint, Integer> blueEggs;

	@Getter(AccessLevel.PACKAGE)
	private HashMap<WorldPoint, Integer> yellowEggs;

	@Inject
	@Getter
	private Client client;

	@Inject
	private ConfigManager configManager;

	@Inject
	private ChatMessageManager chatMessageManager;

	@Inject
	private OverlayManager overlayManager;

	@Inject
	private BAToolsConfig config;

	@Inject
	private ItemManager itemManager;

	@Inject
	private InfoBoxManager infoBoxManager;

	@Inject
	private BAToolsOverlay overlay;

	@Getter
	private Map<NPC, Healer> healers;

	@Getter
	private Instant wave_start;

	@Inject
	private KeyManager keyManager;


	@Provides
	BAToolsConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(BAToolsConfig.class);
	}

	@Override
	protected void startUp() throws Exception
	{
		overlayManager.add(overlay);
		healers = new HashMap<>();
		wave_start = Instant.now();
		foodPressed.clear();
		client.setInventoryDragDelay(config.antiDragDelay());
		keyManager.registerKeyListener(this);
		lastHealer = 0;
		redEggs = new HashMap<>();
		greenEggs = new HashMap<>();
		blueEggs = new HashMap<>();
		yellowEggs = new HashMap<>();
	}

	@Override
	protected void shutDown() throws Exception
	{
		removeCounter();
		healers.clear();
		inGameBit = 0;
		lastInteracted = null;
		overlayManager.remove(overlay);
		inGameBit = 0;
		client.setInventoryDragDelay(5);
		keyManager.unregisterKeyListener(this);
		shiftDown = false;
		collectedEggCount = 0;
		positiveEggCount = 0;
		wrongEggs = 0;
		HpHealed = 0;
	}

	@Subscribe
	public void onWidgetHiddenChanged(WidgetHiddenChanged event)
	{
		Widget weapon = client.getWidget(593, 1);

		if(config.attackStyles()
			&& weapon!=null
			&& inGameBit == 1
			&& (weapon.getText().contains("Crystal halberd") || weapon.getText().contains("Dragon claws"))
			&& client.getWidget(WidgetInfo.BA_ATK_LISTEN_TEXT)!=null)
		{
			String style = client.getWidget(WidgetInfo.BA_ATK_LISTEN_TEXT).getText();

			if(style.contains("Defensive"))
			{
				client.getWidget(WidgetInfo.COMBAT_STYLE_ONE).setHidden(true);
				client.getWidget(WidgetInfo.COMBAT_STYLE_TWO).setHidden(true);
				client.getWidget(WidgetInfo.COMBAT_STYLE_THREE).setHidden(true);
				client.getWidget(WidgetInfo.COMBAT_STYLE_FOUR).setHidden(false);
			}
			else if(style.contains("Aggressive"))
			{
				client.getWidget(WidgetInfo.COMBAT_STYLE_ONE).setHidden(true);
				client.getWidget(WidgetInfo.COMBAT_STYLE_TWO).setHidden(false);
				client.getWidget(WidgetInfo.COMBAT_STYLE_THREE).setHidden(true);
				client.getWidget(WidgetInfo.COMBAT_STYLE_FOUR).setHidden(true);
			}
			else if(style.contains("Controlled"))
			{
				if(weapon.getText().contains("Crystal halberd"))
				{
					client.getWidget(WidgetInfo.COMBAT_STYLE_ONE).setHidden(false);
					client.getWidget(WidgetInfo.COMBAT_STYLE_THREE).setHidden(true);
				}
				else
				{
					client.getWidget(WidgetInfo.COMBAT_STYLE_ONE).setHidden(true);
					client.getWidget(WidgetInfo.COMBAT_STYLE_THREE).setHidden(false);
				}
				client.getWidget(WidgetInfo.COMBAT_STYLE_TWO).setHidden(true);
				client.getWidget(WidgetInfo.COMBAT_STYLE_FOUR).setHidden(true);
			}
			else if(style.contains("Accurate") && weapon.getText().contains("Dragon claws"))
			{
				client.getWidget(WidgetInfo.COMBAT_STYLE_ONE).setHidden(false);
				client.getWidget(WidgetInfo.COMBAT_STYLE_TWO).setHidden(true);
				client.getWidget(WidgetInfo.COMBAT_STYLE_THREE).setHidden(true);
				client.getWidget(WidgetInfo.COMBAT_STYLE_FOUR).setHidden(true);
			}
			else
			{
				client.getWidget(WidgetInfo.COMBAT_STYLE_ONE).setHidden(false);
				client.getWidget(WidgetInfo.COMBAT_STYLE_TWO).setHidden(false);
				client.getWidget(WidgetInfo.COMBAT_STYLE_THREE).setHidden(false);
				client.getWidget(WidgetInfo.COMBAT_STYLE_FOUR).setHidden(false);
			}

		}
		else
		{
			if(client.getWidget(WidgetInfo.COMBAT_STYLE_ONE)!=null)
			{
				client.getWidget(WidgetInfo.COMBAT_STYLE_ONE).setHidden(false);
			}
			if(client.getWidget(WidgetInfo.COMBAT_STYLE_TWO)!=null)
			{
				client.getWidget(WidgetInfo.COMBAT_STYLE_TWO).setHidden(false);
			}
			if(client.getWidget(WidgetInfo.COMBAT_STYLE_THREE)!=null)
			{
				client.getWidget(WidgetInfo.COMBAT_STYLE_THREE).setHidden(false);
			}
			if(client.getWidget(WidgetInfo.COMBAT_STYLE_FOUR)!=null)
			{
				client.getWidget(WidgetInfo.COMBAT_STYLE_FOUR).setHidden(false);
			}
		}
	}
	@Subscribe
	public void onWidgetLoaded(WidgetLoaded event)
	{
		switch (event.getGroupId())
		{
			case WidgetID.BA_REWARD_GROUP_ID:
			{
				Widget pointsWidget = client.getWidget(WidgetID.BA_REWARD_GROUP_ID, 14); //RUNNERS_PASSED
				Widget rewardWidget = client.getWidget(WidgetInfo.BA_REWARD_TEXT);
				if (rewardWidget != null && rewardWidget.getText().contains("<br>5"))
				{
					tickNum = 0;
				}
				if (pointsWidget != null && rewardWidget != null && !rewardWidget.getText().contains(ENDGAME_REWARD_NEEDLE_TEXT) &&
					   !hasAnnounced && client.getVar(Varbits.IN_GAME_BA) == 0)
				{
					wave = new Wave(client);
					wave.setWaveAmounts();
					wave.setWavePoints();
					game.getWaves().add(wave);
					if (config.showSummaryOfPoints())
					{
						announceSomething(wave.getWaveSummary());
					}
				}

				if (config.waveTimes() && rewardWidget != null && rewardWidget.getText().contains(ENDGAME_REWARD_NEEDLE_TEXT) && gameTime != null)
				{
					/*announceTime("Game finished, duration: ", gameTime.getTime(false));
					 announced in BarbarianAssaultPlugin.java*/
					gameTime = null;
					if (config.showTotalRewards())
					{
						announceSomething(game.getGameSummary());
					}
				}

			}
			break;
			case WidgetID.BA_ATTACKER_GROUP_ID:
			{
				setOverlayRound(Role.ATTACKER);
				break;
			}
			case WidgetID.BA_DEFENDER_GROUP_ID:
			{
				setOverlayRound(Role.DEFENDER);
				break;
			}
			case WidgetID.BA_HEALER_GROUP_ID:
			{
				setOverlayRound(Role.HEALER);
				break;
			}
			case WidgetID.BA_COLLECTOR_GROUP_ID:
			{
				setOverlayRound(Role.COLLECTOR);
				break;
			}
		}
	}

	@Subscribe
	public void onGameTick(GameTick event)
	{
		if (config.antiDrag())
		{
			client.setInventoryDragDelay(config.antiDragDelay());
		}

		Widget callWidget = getWidget();

		if (callWidget != null)
		{
			if (callWidget.getTextColor() != pastCall && callWidget.getTextColor() == 16316664)
			{
				tickNum = 0;
			}
			pastCall = callWidget.getTextColor();
		}
		if (inGameBit == 1)
		{
			if (tickNum > 9)
			{
				tickNum = 0;
			}
			if (counter == null)
			{
				addCounter();
			}
			counter.setCount(tickNum);
			if (config.defTimer())
			{
				log.info("" + tickNum++);
			}
		}

		if(config.prayerMetronome() && isAnyPrayerActive())
		{
			for(int i = 0; i < config.prayerMetronomeVolume(); i++)
			{
				client.playSoundEffect(SoundEffectID.GE_INCREMENT_PLOP);
			}
		}
	}
	@Subscribe
	public void onItemSpawned(ItemSpawned itemSpawned)
	{
		int itemId = itemSpawned.getItem().getId();
		WorldPoint worldPoint = itemSpawned.getTile().getWorldLocation();
		HashMap<WorldPoint, Integer> eggMap = getEggMap(itemId);
		if (eggMap !=  null)
		{
			Integer existingQuantity = eggMap.putIfAbsent(worldPoint, 1);
			if (existingQuantity != null)
			{
				eggMap.put(worldPoint, existingQuantity + 1);
			}
		}
	}

	@Subscribe
	public void onItemDespawned(ItemDespawned itemDespawned)
	{
		int itemId = itemDespawned.getItem().getId();
		WorldPoint worldPoint = itemDespawned.getTile().getWorldLocation();
		HashMap<WorldPoint, Integer> eggMap = getEggMap(itemId);

		if (eggMap != null && eggMap.containsKey(worldPoint))
		{
			int quantity = eggMap.get(worldPoint);
			if (quantity > 1)
			{
				eggMap.put(worldPoint, quantity - 1);
			}
			else
			{
				eggMap.remove(worldPoint);
			}
		}
		if (client.getVar(Varbits.IN_GAME_BA) == 0 || !isEgg(itemDespawned.getItem().getId()))
		{
			return;
		}
		if (isUnderPlayer(itemDespawned.getTile()))
		{
			if (overlay.getCurrentRound().getRoundRole() == Role.COLLECTOR)
			{
				positiveEggCount++;
				if (positiveEggCount > 60)
				{
					positiveEggCount = 60;
				}
				collectedEggCount = positiveEggCount - wrongEggs; //true positive - negative egg count
			}
		}
	}

	private Widget getWidget()
	{
		if (client.getWidget(WidgetInfo.BA_DEF_CALL_TEXT) != null)
		{
			return client.getWidget(WidgetInfo.BA_DEF_CALL_TEXT);
		}
		else if (client.getWidget(WidgetInfo.BA_ATK_CALL_TEXT) != null)
		{
			return client.getWidget(WidgetInfo.BA_ATK_CALL_TEXT);
		}
		else if (client.getWidget(WidgetInfo.BA_COLL_CALL_TEXT) != null)
		{
			return client.getWidget(WidgetInfo.BA_COLL_CALL_TEXT);
		}
		else if (client.getWidget(WidgetInfo.BA_HEAL_CALL_TEXT) != null)
		{
			return client.getWidget(WidgetInfo.BA_HEAL_CALL_TEXT);
		}
		return null;
	}

	@Subscribe
	public void onVarbitChanged(VarbitChanged event)
	{
		int inGame = client.getVar(Varbits.IN_GAME_BA);
		if (inGameBit != inGame)
		{
			if (inGameBit == 1)
			{
				pastCall = 0;
				removeCounter();
				foodPressed.clear();
			}
			else
			{
				addCounter();
				lastHealer = 0;
				hasAnnounced = false;
			}
		}
		inGameBit = inGame;
	}

	@Subscribe
	public void onChatMessage(ChatMessage event)
	{
		if (event.getMessage().toLowerCase().startsWith("wave points"))
		{
			hasAnnounced = true;
		}
		if (!event.getType().equals(ChatMessageType.GAMEMESSAGE))
		{
			return;
		}
		int inGame = client.getVar(Varbits.IN_GAME_BA);
		if (inGameBit != inGame)
			return;
		final String message = event.getMessage().toLowerCase();
		final MessageNode messageNode = event.getMessageNode();
		final String nodeValue = Text.removeTags(messageNode.getValue());
		String recolored = null;
		if (event.getMessage().startsWith("---- Wave:"))
		{
			String[] tempMessage = event.getMessage().split(" ");
			currentWave = tempMessage[BA_WAVE_NUM_INDEX];
			collectedEggCount = 0;
			HpHealed = 0;
			positiveEggCount = 0;
			wrongEggs = 0;
			wave_start = Instant.now();
			healers.clear();
			if (currentWave.equals(START_WAVE))
			{
				gameTime = new GameTimer();
				totalHpHealed = 0;
				totalCollectedEggCount = 0;
				game = new Game(client);
			}
			else if (gameTime != null)
			{
				gameTime.setWaveStartTime();
			}
		}
		if (event.getMessage().contains("exploded"))
		{
			wrongEggs++;
			positiveEggCount--;
		}
		if (event.getMessage().contains("You healed"))
		{
			String[] tokens = message.split(" ");
			if (Integer.parseInt(tokens[2]) > 0)
			{
				int Hp = Integer.parseInt(tokens[2]);
				HpHealed += Hp;
			}
		}
		if (message.contains("the wrong type of poisoned food to use"))
		{
			recolored = ColorUtil.wrapWithColorTag(nodeValue, config.wrongPoisonFoodTextColor());
		}
		if (recolored != null)
		{
			messageNode.setValue(recolored);
			chatMessageManager.update(messageNode);
		}
	}

	@Subscribe
	public void onNpcSpawned(NpcSpawned event)
	{
		NPC npc = event.getNpc();

		if (isNpcHealer(npc.getId()))
		{
			if (checkNewSpawn(npc) || Duration.between(wave_start, Instant.now()).getSeconds() < 16)
			{
				int spawnNumber = healers.size();
				healers.put(npc, new Healer(npc, spawnNumber, currentWave));
				//log.info("spawn number: " + spawnNumber + " on wave " + currentWave);
			}
		}
	}

	@Subscribe
	public void onHitsplatApplied(HitsplatApplied hitsplatApplied)
	{
		Actor actor = hitsplatApplied.getActor();

		if (healers.isEmpty() && !(actor instanceof NPC) && lastInteracted == null)
		{
			return;
		}

		for (Healer healer : healers.values())
		{
			if (healer.getNpc() == actor && actor == lastInteracted)
			{
				healer.setFoodRemaining(healer.getFoodRemaining() - 1);
			}
		}
	}

	private void announceSomething(final ChatMessageBuilder chatMessage)
	{
		chatMessageManager.queue(QueuedMessage.builder()
			   .type(ChatMessageType.CONSOLE)
			   .runeLiteFormattedMessage(chatMessage.build())
			   .build());
	}

	String getCollectorHeardCall()
	{
		Widget widget = client.getWidget(WidgetInfo.BA_COLL_LISTEN_TEXT);
		String call = null;

		if (widget != null)
		{
			call = widget.getText();
		}

		return call;
	}

	Map<WorldPoint, Integer> getCalledEggMap()
	{
		Map<WorldPoint, Integer> map;
		String calledEgg = getCollectorHeardCall();

		if (calledEgg == null)
		{
			return null;
		}

		switch (calledEgg)
		{
			case "Red eggs":
				map = redEggs;
				break;
			case "Green eggs":
				map = greenEggs;
				break;
			case "Blue eggs":
				map = blueEggs;
				break;
			default:
				map = null;
		}

		return map;
	}

	static Color getEggColor(String str)
	{
		Color color;

		if (str == null)
		{
			return null;
		}

		if (str.startsWith("Red"))
		{
			color = Color.RED;
		}
		else if (str.startsWith("Green"))
		{
			color = Color.GREEN;
		}
		else if (str.startsWith("Blue"))
		{
			color = Color.CYAN;
		}
		else if (str.startsWith("Yellow"))
		{
			color = Color.YELLOW;
		}
		else
		{
			color = null;
		}

		return color;
	}

	private HashMap<WorldPoint, Integer> getEggMap(int itemID)
	{
		switch (itemID)
		{
			case ItemID.RED_EGG:
				return redEggs;
			case ItemID.GREEN_EGG:
				return greenEggs;
			case ItemID.BLUE_EGG:
				return blueEggs;
			case ItemID.YELLOW_EGG:
				return yellowEggs;
			default:
				return null;
		}
	}

	private void setOverlayRound(Role role)
	{
		// Prevent changing roles when a role is already set, as widgets can be
		// loaded multiple times in game from eg. opening and closing the horn
		// of glory.
		if (overlay.getCurrentRound() != null)
		{
			return;
		}

		overlay.setCurrentRound(new Round(role));
	}

	private boolean isEgg(int itemID)
	{
		return (itemID == ItemID.RED_EGG || itemID == ItemID.GREEN_EGG
			   || itemID == ItemID.BLUE_EGG || itemID == ItemID.YELLOW_EGG);
	}

	private boolean isUnderPlayer(Tile tile)
	{
		Player local = client.getLocalPlayer();
		if (local == null)
		{
			return false;
		}

		return (tile.getWorldLocation().equals(local.getWorldLocation()));
	}

	public Font getFont()
	{
		return font;
	}

	public Image getClockImage()
	{
		return clockImage;
	}

	public int getListenItemId(WidgetInfo listenInfo)
	{
		Widget listenWidget = client.getWidget(listenInfo);

		if (listenWidget != null)
		{
			switch (listenWidget.getText())
			{
				case "Tofu":
					return ItemID.TOFU;
				case "Crackers":
					return ItemID.CRACKERS;
				case "Worms":
					return ItemID.WORMS;
				case "Pois. Worms":
					return ItemID.POISONED_WORMS;
				case "Pois. Tofu":
					return ItemID.POISONED_TOFU;
				case "Pois. Meat":
					return ItemID.POISONED_MEAT;
			}
		}

		return -1;
	}

	@Subscribe
	public void onNpcDespawned(NpcDespawned event)
	{
		if (healers.remove(event.getNpc()) != null && healers.isEmpty())
		{
			healers.clear();
		}
	}

	@Subscribe
	public void onInteractingChanged(InteractingChanged event)
	{
		Actor opponent = event.getTarget();

		if (opponent != null && opponent instanceof NPC && isNpcHealer(((NPC) opponent).getId()) && event.getSource() != client.getLocalPlayer())
		{
			lastInteracted = opponent;
		}
	}

	public static boolean isNpcHealer(int npcId)
	{
		return npcId == NpcID.PENANCE_HEALER ||
				npcId == NpcID.PENANCE_HEALER_5766 ||
				npcId == NpcID.PENANCE_HEALER_5767 ||
				npcId == NpcID.PENANCE_HEALER_5768 ||
				npcId == NpcID.PENANCE_HEALER_5769 ||
				npcId == NpcID.PENANCE_HEALER_5770 ||
				npcId == NpcID.PENANCE_HEALER_5771 ||
				npcId == NpcID.PENANCE_HEALER_5772 ||
				npcId == NpcID.PENANCE_HEALER_5773 ||
				npcId == NpcID.PENANCE_HEALER_5774;
	}

	@Subscribe
	public void onMenuEntryAdded(MenuEntryAdded event)
	{
		if (config.highlightCollectorEggs() && overlay.getCurrentRound() != null && overlay.getCurrentRound().getRoundRole() == Role.COLLECTOR)
		{
			String calledEgg = getCollectorHeardCall();
			String target = event.getTarget();
			String option = event.getOption();
			String targetClean = target.substring(target.indexOf('>') + 1);
			String optionClean = option.substring(option.indexOf('>') + 1);

			if ("Take".equals(optionClean)) {
				Color highlightColor = null;

				if (calledEgg != null && calledEgg.startsWith(targetClean)) {
					highlightColor = getEggColor(targetClean);
				} else if ("Yellow egg".equals(targetClean)) {
					// Always show yellow egg
					highlightColor = Color.YELLOW;
				}

				if (highlightColor != null) {
					MenuEntry[] menuEntries = client.getMenuEntries();
					MenuEntry last = menuEntries[menuEntries.length - 1];
					last.setTarget(ColorUtil.prependColorTag(targetClean, highlightColor));
					client.setMenuEntries(menuEntries);
				}
			}
		}
		if (config.calls() && getWidget() != null && event.getTarget().endsWith("horn") && !event.getTarget().contains("Unicorn"))
		{
			MenuEntry[] menuEntries = client.getMenuEntries();
			Widget callWidget = getWidget();
			String call = Calls.getOption(callWidget.getText());
			MenuEntry correctCall = null;

			entries.clear();
			for (MenuEntry entry : menuEntries)
			{
				String option = entry.getOption();
				if (option.equals(call))
				{
					correctCall = entry;
				}
				else if (!option.startsWith("Tell-"))
				{
					entries.add(entry);
				}
			}

			if (correctCall != null) //&& callWidget.getTextColor()==16316664)
			{
				entries.add(correctCall);
				client.setMenuEntries(entries.toArray(new MenuEntry[entries.size()]));
			}
		}
		final int itemId = event.getIdentifier();
		String option = Text.removeTags(event.getOption()).toLowerCase();
		String target = Text.removeTags(event.getTarget()).toLowerCase();
		if (config.swapDestroyEggs() && (target.equals("red egg") || target.equals("green egg") || target.equals("blue egg")))
		{
			swap("destroy", option, target, false);
		}
		if (config.swapCollectorBag() && target.equals("collection bag"))
		{
			swap("empty", option, target, false);
		}
		if (config.shiftWalkHere() && shiftDown && !event.getTarget().equals("<col=ffff>Healer item machine") &&
			   !option.equals("Stock-Up") && !option.equals("Take-Vial") &&
			   !option.equals("Take-Tofu") && !option.equals("Take-Worms") &&
			   !option.equals("Take-Meat") &&
			   !target.contains("Nuff"))
		{
		// Keep moving 'Walk here' to the end of the entries (left-click option)
		MenuEntry[] entries = client.getMenuEntries();
		int walkIdx = searchIndex(entries, "Walk here", "", false);
			if (walkIdx > 0 && walkIdx <= entries.length)
			{
			  MenuEntry walkHere = entries[walkIdx];
			  MenuEntry currentTop = entries[entries.length - 1];

			  entries[walkIdx] = currentTop;
			  entries[entries.length - 1] = walkHere;

			  client.setMenuEntries(entries);
			}
		}


		if (config.swapLadder() && option.equals("climb-down") && target.equals("ladder"))
		{
			swap("quick-start", option, target, true);
		}
		if(config.removeBA() && client.getVar(Varbits.IN_GAME_BA) == 1 && !option.contains("tell-"))//if in barbarian assault and menu isnt from a horn
		{
			if(itemId == ItemID.LOGS && !target.contains("healing vial"))
			{
				if(client.getWidget(WidgetInfo.BA_DEF_ROLE_TEXT) == null)
					remove(new String[]{"take", "light"}, target, true);
				else//remove "Light" option (and "Take" option if not defender).
					remove("light", target, true);
			}
			else if(option.equals("attack") && client.getWidget(WidgetInfo.BA_ATK_ROLE_TEXT) == null && !target.equals("queen spawn"))//if not attacker
			{//remove attack option from everything but queen spawns
				remove(option, target, true);
			}
			else if((option.equals("fix")) && client.getWidget(WidgetInfo.BA_DEF_ROLE_TEXT) == null)//if not defender
			{
				remove(option, target, true);
			}
			else if((option.equals("block") && target.equals("penance cave") && config.removePenanceCave()))
			{//the check for option requires checking target as well because defensive attack style option is also called "block".
				remove(option, target, true);
			}
			else if((option.equals("load")) && client.getWidget(WidgetInfo.BA_COLL_ROLE_TEXT) == null)//if not collector, remove hopper options
			{
				remove(new String[]{option, "look-in"}, target, true);
			}
			else if(config.removeWrongEggs() && option.equals("take"))
			{
				Widget eggToColl = client.getWidget(WidgetInfo.BA_COLL_LISTEN_TEXT);
				if(eggToColl != null)//if we're a collector
				{
					List<Integer> eggsToHide = new ArrayList<>();
					eggsToHide.add(ItemID.HAMMER);
					switch(eggToColl.getText())//choose which eggs to hide take option for
					{
						case "Red eggs":
							eggsToHide.add(ItemID.BLUE_EGG);
							eggsToHide.add(ItemID.GREEN_EGG);
							break;
						case "Blue eggs":
							eggsToHide.add(ItemID.RED_EGG);
							eggsToHide.add(ItemID.GREEN_EGG);
							break;
						case "Green eggs":
							eggsToHide.add(ItemID.RED_EGG);
							eggsToHide.add(ItemID.BLUE_EGG);
							break;
					}
					if(eggsToHide.contains(itemId))
					{
						remove(option, target, true);//hide wrong eggs
					}
				}
				else
				{
					List<Integer> defenderItems = Arrays.asList(ItemID.HAMMER, ItemID.TOFU, ItemID.CRACKERS, ItemID.WORMS);//logs are handled separately due to hiding "light" option too.
					if(client.getWidget(WidgetInfo.BA_DEF_ROLE_TEXT) == null || !defenderItems.contains(itemId))//if not defender, or item is not a defenderItem
					{
						remove(option, target, true);//hide everything except hammer/logs and bait if Defender
					}
				}
			}
		}
		if(client.getWidget(WidgetInfo.BA_HEAL_CALL_TEXT) == getWidget() && lastHealer != 0 && inGameBit == 1 && config.ctrlHealer() && ctrlDown)
		{
			MenuEntry[] menuEntries = client.getMenuEntries();
			MenuEntry correctHealer = null;
			entries.clear();

			for (MenuEntry entry : menuEntries)
			{

				if((entry.getIdentifier() == lastHealer  && entry.getOption().equals("Use"))
						||
						(
								(entry.getTarget().equals("<col=ff9040>Poisoned meat") || entry.getTarget().equals("<col=ff9040>Poisoned worms") || entry.getTarget().equals("<col=ff9040>Poisoned tofu"))
								&&
								entry.getOption().equals("Use")
						)
				)
				{
					correctHealer = entry;
				}
				else
				{
					log.info((entry.getIdentifier() == lastHealer  && entry.getOption().equals("Use")) + " "+((entry.getTarget().equals("<col=ff9040>Poisoned meat") || entry.getTarget().equals("<col=ff9040>Poisoned worms") || entry.getTarget().equals("<col=ff9040>Poisoned tofu")) && entry.getOption().equals("Use")) );
				}
			}
			if (correctHealer != null)
			{
				entries.add(correctHealer);
			}
			client.setMenuEntries(entries.toArray(new MenuEntry[entries.size()]));
		}
		if (config.swapLadder() && option.equals("climb-down") && target.equals("ladder"))
		{
			swap("Quick-start", option, target, true);
		}
		if(config.removeBA() && client.getVar(Varbits.IN_GAME_BA) == 1 && !option.contains("tell-"))//if in barbarian assault and menu isnt from a horn
		{
			if(option.equals("use"))
			{
				if (config.removeHealWrongFood()) {
					Widget healer = client.getWidget(WidgetInfo.BA_HEAL_LISTEN_TEXT);
					if (healer != null) {
						String item = target.split("-")[0].trim();
						List<String> poison = Arrays.asList("poisoned tofu", "poisoned meat", "poisoned worms");
						List<String> vials = Arrays.asList("healing vial", "healing vial(1)", "healing vial(2)", "healing vial(3)", "healing vial(4)");//"healing vial(4)"
						if (poison.contains(item)) {//if item is a poison item
							int calledPoison = 0;
							switch (healer.getText())//choose which poison to hide the use/destroy option for
							{
								case "Pois. Tofu":
									calledPoison = ItemID.POISONED_TOFU;
									break;
								case "Pois. Meat":
									calledPoison = ItemID.POISONED_MEAT;
									break;
								case "Pois. Worms":
									calledPoison = ItemID.POISONED_WORMS;
									break;
							}
							System.out.println(target.equals(item));
							if (target.equals(item))//if targeting the item itself
							{
								if (calledPoison != 0 && itemId != calledPoison)//if no call or chosen item is not the called one
								{
									remove(new String[]{"use", "destroy", "examine"}, target, true);//remove options
								}
							} else if (!target.contains("penance healer")) {
								remove(option, target, true);
							}
						} else if (vials.contains(item))//if item is the healer's healing vial
						{

							if (!target.equals(item))//if target is not the vial itself
							{

								if (!target.contains("level") || target.contains("penance") || target.contains("queen spawn"))//if someone has "penance" or "queen spawn" in their name, gg...
								{
									remove(option, target, true);
								}
							}
						}
					}
				}
			}
			else if(option.equals("attack") && client.getWidget(WidgetInfo.BA_ATK_ROLE_TEXT) == null && !target.equals("queen spawn"))//if not attacker
			{//remove attack option from everything but queen spawns
				remove(option, target, true);
			}
			else if((option.equals("fix") || (option.equals("block") && target.equals("penance cave"))) && client.getWidget(WidgetInfo.BA_DEF_ROLE_TEXT) == null)//if not defender
			{//the check for option requires checking target as well because defensive attack style option is also called "block".
				remove(option, target, true);
			}
			else if((option.equals("load")) && client.getWidget(WidgetInfo.BA_COLL_ROLE_TEXT) == null)//if not collector, remove hopper options
			{
				remove(new String[]{option, "look-in"}, target, true);
			}
			else if(config.removeWrongEggs() && option.equals("take"))
			{
				Widget eggToColl = client.getWidget(WidgetInfo.BA_COLL_LISTEN_TEXT);
				if(eggToColl != null)//if we're a collector
				{
					List<Integer> eggsToHide = new ArrayList<>();
					eggsToHide.add(ItemID.HAMMER);
					switch(eggToColl.getText())//choose which eggs to hide take option for
					{
						case "Red eggs":
							eggsToHide.add(ItemID.BLUE_EGG);
							eggsToHide.add(ItemID.GREEN_EGG);
							break;
						case "Blue eggs":
							eggsToHide.add(ItemID.RED_EGG);
							eggsToHide.add(ItemID.GREEN_EGG);
							break;
						case "Green eggs":
							eggsToHide.add(ItemID.RED_EGG);
							eggsToHide.add(ItemID.BLUE_EGG);
							break;
					}
					if(eggsToHide.contains(itemId))
					{
						remove(option, target, true);//hide wrong eggs
					}
				}
				else
				{
					List<Integer> defenderItems = Arrays.asList(ItemID.HAMMER, ItemID.TOFU, ItemID.CRACKERS, ItemID.WORMS);//logs are handled separately due to hiding "light" option too.
					if(client.getWidget(WidgetInfo.BA_DEF_ROLE_TEXT) == null || !defenderItems.contains(itemId))//if not defender, or item is not a defenderItem
					{
						remove(option, target, true);//hide everything except hammer/logs and bait if Defender
					}
				}
			}
		}


		if (config.healerGreenColor() && (event.getTarget().contains("Penance Healer")))
		{

			MenuEntry[] menuEntries = client.getMenuEntries();
			MenuEntry lastEntry = menuEntries[menuEntries.length - 1];
			String targett = lastEntry.getTarget();

			if (foodPressed.containsKey(lastEntry.getIdentifier()))
			{
				lastEntry.setTarget(lastEntry.getTarget().split("\\(")[0] + "(" + Duration.between(foodPressed.get(lastEntry.getIdentifier()), Instant.now()).getSeconds() + ")");
				if (Duration.between(foodPressed.get(lastEntry.getIdentifier()), Instant.now()).getSeconds() > 20)
				{
					lastEntry.setTarget(lastEntry.getTarget().replace("<col=ffff00>", "<col=2bff63>"));
				}
			}
			else
			{
				lastEntry.setTarget(targett.replace("<col=ffff00>", "<col=2bff63>"));

			}

			client.setMenuEntries(menuEntries);
		}

		if (client.getWidget(WidgetInfo.BA_COLL_LISTEN_TEXT) != null && inGameBit == 1 && config.eggBoi() && event.getTarget().endsWith("egg") && shiftDown)
		{
			String[] currentCall = client.getWidget(WidgetInfo.BA_COLL_LISTEN_TEXT).getText().split(" ");
			log.info("1 " + currentCall[0]);
			MenuEntry[] menuEntries = client.getMenuEntries();
			MenuEntry correctEgg = null;
			entries.clear();

			for (MenuEntry entry : menuEntries)
			{
				if (entry.getTarget().contains(currentCall[0]) && entry.getOption().equals("Take"))
				{
					correctEgg = entry;
				}
				else if (!entry.getOption().startsWith("Take"))
				{
					entries.add(entry);
				}
			}
			if (correctEgg != null)
			{
				entries.add(correctEgg);
			}
			client.setMenuEntries(entries.toArray(new MenuEntry[entries.size()]));
		}
		if (client.getWidget(WidgetInfo.BA_ATK_LISTEN_TEXT) != null && inGameBit == 1 && config.attackStyles() && shiftDown)
		{
			MenuEntry[] menuEntries = client.getMenuEntries();
			MenuEntry correctEgg = null;
			entries.clear();

			for (MenuEntry entry : menuEntries)
			{
				if (entry.getOption().contains("Walk here"))
				{
					entries.add(entry);
				}
			}
			client.setMenuEntries(entries.toArray(new MenuEntry[entries.size()]));
		}

		if (client.getWidget(WidgetInfo.BA_HEAL_LISTEN_TEXT) != null && inGameBit == 1 && config.osHelp() && event.getTarget().equals("<col=ffff>Healer item machine") && shiftDown)
		{
			String[] currentCall = client.getWidget(WidgetInfo.BA_HEAL_LISTEN_TEXT).getText().split(" ");

			if (currentCall[0].contains("Pois.")) {
				MenuEntry[] menuEntries = client.getMenuEntries();
				MenuEntry correctEgg = null;
				entries.clear();

				for (MenuEntry entry : menuEntries) {
					if (entry.getOption().equals("Take-" + currentCall[1])) {
						correctEgg = entry;
					}
				}
				if (correctEgg != null) {
					entries.add(correctEgg);
					client.setMenuEntries(entries.toArray(new MenuEntry[entries.size()]));
				}
			}
		}

	}

	@Subscribe
	public void onMenuOptionClicked(MenuOptionClicked event)
	{
		String target = event.getMenuTarget();

		if(config.tagging() && (event.getMenuTarget().contains("Penance Ranger") || event.getMenuTarget().contains("Penance Fighter")))
		{
			if (event.getMenuOption().contains("Attack"))
			{
				foodPressed.put(event.getId(), Instant.now());
			}
			log.info(target);
		}

		if (config.healerMenuOption() && target.contains("Penance Healer") && target.contains("<col=ff9040>Poisoned") && target.contains("->"))
		{
			foodPressed.put(event.getId(), Instant.now());
			lastHealer = event.getId();
			log.info("Last healer changed: " + lastHealer);
		}
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged event)
	{
		if (config.antiDrag())
		{
			client.setInventoryDragDelay(config.antiDragDelay());
		}
	}

	private void addCounter()
	{
		if (!config.defTimer() || counter != null)
		{
			return;
		}

		int itemSpriteId = ItemID.FIGHTER_TORSO;

		BufferedImage taskImg = itemManager.getImage(itemSpriteId);
		counter = new CycleCounter(taskImg, this, tickNum);

		infoBoxManager.addInfoBox(counter);
	}

	private void removeCounter()
	{
		if (counter == null)
		{
			return;
		}

		infoBoxManager.removeInfoBox(counter);
		counter = null;
	}

	private void remove(String option, String target, boolean strict)
	{
		MenuEntry[] entries = client.getMenuEntries();
		int idx = searchIndex(entries, option, target, strict);
		if(idx >= 0 && entries[idx] != null)
		{
			entries = ArrayUtils.removeElement(entries, entries[idx]);
			client.setMenuEntries(entries);
		}
	}

	private void remove(String[] options, String target, boolean strict)
	{
		MenuEntry[] entries = client.getMenuEntries();
		for(int i = 0; i < options.length; i++)
		{
			int idx = searchIndex(entries, options[i], target, strict);
			if(idx >= 0 && entries[idx] != null)
				entries = ArrayUtils.removeElement(entries, entries[idx]);
		}

		client.setMenuEntries(entries);
	}

	private void swap(String optionA, String optionB, String target, boolean strict)
	{
		MenuEntry[] entries = client.getMenuEntries();

		int idxA = searchIndex(entries, optionA, target, strict);
		int idxB = searchIndex(entries, optionB, target, strict);

		if (idxA >= 0 && idxB >= 0)
		{
			MenuEntry entry = entries[idxA];
			entries[idxA] = entries[idxB];
			entries[idxB] = entry;

			client.setMenuEntries(entries);
		}
	}

	private int searchIndex(MenuEntry[] entries, String option, String target, boolean strict)
	{
		for (int i = entries.length - 1; i >= 0; i--)
		{
			MenuEntry entry = entries[i];
			String entryOption = Text.removeTags(entry.getOption()).toLowerCase();
			String entryTarget = Text.removeTags(entry.getTarget()).toLowerCase();

			if (strict)
			{
				if (entryOption.equals(option) && entryTarget.equals(target))
				{
					return i;
				}
			}
			else
			{
				if (entryOption.contains(option.toLowerCase()) && entryTarget.equals(target))
				{
					return i;
				}
			}
		}

		return -1;
	}

	private static WorldPoint rotate(WorldPoint point, int rotation)
	{
		int chunkX = point.getX() & ~(CHUNK_SIZE - 1);
		int chunkY = point.getY() & ~(CHUNK_SIZE - 1);
		int x = point.getX() & (CHUNK_SIZE - 1);
		int y = point.getY() & (CHUNK_SIZE - 1);
		switch (rotation)
		{
			case 1:
				return new WorldPoint(chunkX + y, chunkY + (CHUNK_SIZE - 1 - x), point.getPlane());
			case 2:
				return new WorldPoint(chunkX + (CHUNK_SIZE - 1 - x), chunkY + (CHUNK_SIZE - 1 - y), point.getPlane());
			case 3:
				return new WorldPoint(chunkX + (CHUNK_SIZE - 1 - y), chunkY + x, point.getPlane());
		}
		return point;
	}

	private boolean checkNewSpawn(NPC npc)
	{
		int regionId = 7509;
		int regionX = 42;
		int regionY = 46;
		int z = 0;

		// world point of the tile marker
		WorldPoint worldPoint = new WorldPoint(
				((regionId >>> 8) << 6) + regionX,
				((regionId & 0xff) << 6) + regionY,
				z
		);

		int[][][] instanceTemplateChunks = client.getInstanceTemplateChunks();
		for (int x = 0; x < instanceTemplateChunks[z].length; ++x)
		{
			for (int y = 0; y < instanceTemplateChunks[z][x].length; ++y)
			{
				int chunkData = instanceTemplateChunks[z][x][y];
				int rotation = chunkData >> 1 & 0x3;
				int templateChunkY = (chunkData >> 3 & 0x7FF) * CHUNK_SIZE;
				int templateChunkX = (chunkData >> 14 & 0x3FF) * CHUNK_SIZE;
				if (worldPoint.getX() >= templateChunkX && worldPoint.getX() < templateChunkX + CHUNK_SIZE
						&& worldPoint.getY() >= templateChunkY && worldPoint.getY() < templateChunkY + CHUNK_SIZE)
				{
					WorldPoint p = new WorldPoint(client.getBaseX() + x * CHUNK_SIZE + (worldPoint.getX() & (CHUNK_SIZE - 1)),
							client.getBaseY() + y * CHUNK_SIZE + (worldPoint.getY() & (CHUNK_SIZE - 1)),
							worldPoint.getPlane());
					p = rotate(p, rotation);
					if (p.distanceTo(npc.getWorldLocation()) < 5)
					{
						return true;
					}
				}
			}
		}
		return false;
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
		if (e.getKeyCode() == KeyEvent.VK_CONTROL)
		{
			ctrlDown = true;
		}
	}

	@Override
	public void keyReleased(KeyEvent e)
	{
		if (e.getKeyCode() == KeyEvent.VK_SHIFT)
		{
			shiftDown = false;
		}
		if (e.getKeyCode() == KeyEvent.VK_CONTROL)
		{
			ctrlDown = false;
		}
	}

	private boolean isAnyPrayerActive()
	{
		for (Prayer pray : Prayer.values())//Check if any prayers are active
		{
			if (client.isPrayerActive(pray))
			{
				return true;
			}
		}

		return false;
	}
}