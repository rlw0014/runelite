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
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Actor;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.ItemID;
import net.runelite.api.MenuEntry;
import net.runelite.api.MessageNode;
import net.runelite.api.NPC;
import net.runelite.api.Prayer;
import net.runelite.api.SoundEffectID;
import net.runelite.api.Varbits;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.ConfigChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.MenuEntryAdded;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.api.events.VarbitChanged;
import net.runelite.api.events.WidgetHiddenChanged;
import net.runelite.api.events.WidgetLoaded;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetID;
import net.runelite.api.widgets.WidgetInfo;
import net.runelite.client.chat.ChatMessageManager;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.game.ItemManager;
import net.runelite.client.input.KeyListener;
import net.runelite.client.input.KeyManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.ui.overlay.infobox.InfoBoxManager;
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
	private BufferedImage fighterImage, rangerImage, healerImage, runnerImage;
	int inGameBit = 0;
	int tickNum;
	int pastCall = 0;
	private int lastHealer;
	private static final int BA_WAVE_NUM_INDEX = 2;
	private GameTimer gameTime;
	private static final String START_WAVE = "1";
	private String currentWave = START_WAVE;
	private final List<MenuEntry> entries = new ArrayList<>();
	private HashMap<Integer, Instant> foodPressed = new HashMap<>();
	private CycleCounter counter;

	private BAMonsterBox[] monsterDeathInfoBox = new BAMonsterBox[4];

	private static final String ENDGAME_REWARD_NEEDLE_TEXT = "<br>5";

	private Actor lastInteracted;
	private boolean shiftDown;
	private boolean ctrlDown;

	@Inject
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
		fighterImage = ImageUtil.getResourceStreamFromClass(getClass(), "fighter.png");
		rangerImage = ImageUtil.getResourceStreamFromClass(getClass(), "ranger.png");
		runnerImage = ImageUtil.getResourceStreamFromClass(getClass(), "runner.png");
		healerImage = ImageUtil.getResourceStreamFromClass(getClass(), "healer.png");
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
		gameTime = null;
		currentWave = START_WAVE;
		client.setInventoryDragDelay(5);
		keyManager.unregisterKeyListener(this);
		shiftDown = false;
	}

	@Subscribe
	public void onWidgetLoaded(WidgetLoaded event)
	{
		switch (event.getGroupId())
		{
			case WidgetID.BA_REWARD_GROUP_ID:
			{
				Widget rewardWidget = client.getWidget(WidgetInfo.BA_REWARD_TEXT);

				if (rewardWidget != null && rewardWidget.getText().contains(ENDGAME_REWARD_NEEDLE_TEXT) && gameTime != null)
				{
					gameTime = null;
				}
			}
		}
	}

	@Subscribe
	public void onWidgetHiddenChanged(WidgetHiddenChanged event)
	{
		Widget weapon = client.getWidget(593, 1);

		if (config.attackStyles()
			&& weapon != null
			&& inGameBit == 1
			&& (weapon.getText().contains("Crystal halberd") || weapon.getText().contains("Dragon claws"))
			&& client.getWidget(WidgetInfo.BA_ATK_LISTEN_TEXT) != null)
		{
			String style = client.getWidget(WidgetInfo.BA_ATK_LISTEN_TEXT).getText();

			if (style.contains("Defensive"))
			{
				client.getWidget(WidgetInfo.COMBAT_STYLE_ONE).setHidden(true);
				client.getWidget(WidgetInfo.COMBAT_STYLE_TWO).setHidden(true);
				client.getWidget(WidgetInfo.COMBAT_STYLE_THREE).setHidden(true);
				client.getWidget(WidgetInfo.COMBAT_STYLE_FOUR).setHidden(false);
			}
			else if (style.contains("Aggressive"))
			{
				client.getWidget(WidgetInfo.COMBAT_STYLE_ONE).setHidden(true);
				client.getWidget(WidgetInfo.COMBAT_STYLE_TWO).setHidden(false);
				client.getWidget(WidgetInfo.COMBAT_STYLE_THREE).setHidden(true);
				client.getWidget(WidgetInfo.COMBAT_STYLE_FOUR).setHidden(true);
			}
			else if (style.contains("Controlled"))
			{
				if (weapon.getText().contains("Crystal halberd"))
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
			else if (style.contains("Accurate") && weapon.getText().contains("Dragon claws"))
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
		if (config.defTimer() && inGameBit == 1)
		{
			if (tickNum > 9)
			{
				tickNum = 0;
			}
			if (counter == null)
			{
				addCounter();
			}
			counter.setCount(tickNum++);
		}

		if (config.prayerMetronome() && isAnyPrayerActive())
		{
			for (int i = 0; i < config.prayerMetronomeVolume(); i++)
			{
				client.playSoundEffect(SoundEffectID.GE_INCREMENT_PLOP);
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
			}
		}

		inGameBit = inGame;
	}

	@Subscribe
	public void onMenuEntryAdded(MenuEntryAdded event)
	{
		final int itemId = event.getIdentifier();
		String option = Text.removeTags(event.getOption()).toLowerCase();
		String target = Text.removeTags(event.getTarget()).toLowerCase();

		//Incorrect call remover
		if (config.calls() && getWidget() != null && event.getTarget().endsWith("horn") && inGameBit == 1)
		{
			MenuEntry[] menuEntries = client.getMenuEntries();
			Widget callWidget = getWidget();
			String call = callWidget.getText();
			MenuEntry correctCall = null;

			entries.clear();

			for (MenuEntry entry : menuEntries)
			{
				if (entry.getOption().contains("Tell-") && call.toLowerCase().contains(entry.getOption().substring(5)))
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

		//Ladder swap
		if (config.swapLadder() && option.equals("climb-down") && target.equals("ladder"))
		{
			swap("quick-start", option, target, true);
		}

		//Ctrl Healer
		if (client.getWidget(WidgetInfo.BA_HEAL_CALL_TEXT) == getWidget() && lastHealer != 0 && inGameBit == 1 && config.ctrlHealer() && ctrlDown)
		{
			MenuEntry[] menuEntries = client.getMenuEntries();
			MenuEntry correctHealer = null;
			entries.clear();

			for (MenuEntry entry : menuEntries)
			{

				if ((entry.getIdentifier() == lastHealer  && entry.getOption().equals("Use"))
						||
						(
								(entry.getTarget().equals("<col=ff9040>Poisoned meat") || entry.getTarget().equals("<col=ff9040>Poisoned worms") || entry.getTarget().equals("<col=ff9040>Poisoned tofu"))
								&&
										(entry.getOption().equals("Use") || entry.getOption().equals("Cancel"))
						)
				)
				{
					correctHealer = entry;
				}
				else if (!option.startsWith("use"))
				{
					entries.add(entry);
					//log.info((entry.getIdentifier() == lastHealer  && entry.getOption().equals("Use")) + " "+((entry.getTarget().equals("<col=ff9040>Poisoned meat") || entry.getTarget().equals("<col=ff9040>Poisoned worms") || entry.getTarget().equals("<col=ff9040>Poisoned tofu")) && entry.getOption().equals("Use")) );
				}
				//log.info((entry.getIdentifier() == lastHealer  && entry.getOption().equals("Use"))+ " " + ((entry.getTarget().equals("<col=ff9040>Poisoned meat") || entry.getTarget().equals("<col=ff9040>Poisoned worms") || entry.getTarget().equals("<col=ff9040>Poisoned tofu")) && entry.getOption().equals("Use")));
				//log.info("Entry identifier = "+ entry.getIdentifier() + "Entry target = "+entry.getTarget());
			}
			if (correctHealer != null)
			{
				entries.add(correctHealer);
			}
			client.setMenuEntries(entries.toArray(new MenuEntry[entries.size()]));
		}

		if (config.removeBA() && client.getVar(Varbits.IN_GAME_BA) == 1 && !option.contains("tell-"))//if in barbarian assault and menu isnt from a horn
		{
			if (itemId == ItemID.LOGS && !target.contains("healing vial"))
			{
				if (client.getWidget(WidgetInfo.BA_DEF_ROLE_TEXT) == null)
					remove(new String[]{"take", "light"}, target, true);
				else //remove "Light" option (and "Take" option if not defender).
					remove("light", target, true);
			}
			else if (option.equals("use"))
			{
				if (config.removeHealWrongFood())
				{
					Widget healer = client.getWidget(WidgetInfo.BA_HEAL_LISTEN_TEXT);
					if (healer != null)
					{
						String item = target.split("-")[0].trim();
						List<String> poison = Arrays.asList("poisoned tofu", "poisoned meat", "poisoned worms");
						List<String> vials = Arrays.asList("healing vial", "healing vial(1)", "healing vial(2)", "healing vial(3)", "healing vial(4)");//"healing vial(4)"
						if (poison.contains(item)) //if item is a poison item
						{
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
							}
							else if (!target.contains("penance healer"))
							{
								remove(option, target, true);
							}
						}
						else if (vials.contains(item))//if item is the healer's healing vial
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
			else if (option.equals("attack") && client.getWidget(WidgetInfo.BA_ATK_ROLE_TEXT) == null && !target.equals("queen spawn"))//if not attacker
			{ //remove attack option from everything but queen spawns
				remove(option, target, true);
			}
			else if ((option.equals("fix") || (option.equals("block") && target.equals("penance cave") && config.removePenanceCave())) && client.getWidget(WidgetInfo.BA_DEF_ROLE_TEXT) == null)//if not defender
			{ //the check for option requires checking target as well because defensive attack style option is also called "block".
				remove(option, target, true);
			}
			else if ((option.equals("load")) && client.getWidget(WidgetInfo.BA_COLL_ROLE_TEXT) == null)//if not collector, remove hopper options
			{
				remove(new String[]{option, "look-in"}, target, true);
			}
			else if (config.removeWrongEggs() && option.equals("take"))
			{
				Widget eggToColl = client.getWidget(WidgetInfo.BA_COLL_LISTEN_TEXT);
				if (eggToColl != null)//if we're a collector
				{
					List<Integer> eggsToHide = new ArrayList<>();
					eggsToHide.add(ItemID.HAMMER);
					switch (eggToColl.getText())//choose which eggs to hide take option for
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
					if (eggsToHide.contains(itemId))
					{
						remove(option, target, true);//hide wrong eggs
					}
				}
				else
				{
					List<Integer> defenderItems = Arrays.asList(ItemID.HAMMER, ItemID.TOFU, ItemID.CRACKERS, ItemID.WORMS);//logs are handled separately due to hiding "light" option too.
					if (client.getWidget(WidgetInfo.BA_DEF_ROLE_TEXT) == null || !defenderItems.contains(itemId))//if not defender, or item is not a defenderItem
					{
						remove(option, target, true);//hide everything except hammer/logs and bait if Defender
					}
				}
			}
		}


		if ((event.getTarget().contains("Penance Healer") || event.getTarget().contains("Penance Fighter") || event.getTarget().contains("Penance Ranger")))
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

		//Collector helper
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

		//Attacker shift to walk here
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

		//Shift healer OS
		if (client.getWidget(WidgetInfo.BA_HEAL_LISTEN_TEXT) != null && inGameBit == 1 && config.osHelp() && event.getTarget().equals("<col=ffff>Healer item machine") && shiftDown)
		{
			String[] currentCall = client.getWidget(WidgetInfo.BA_HEAL_LISTEN_TEXT).getText().split(" ");

			if (!currentCall[0].contains("Pois."))
			{
				return;
			}

			MenuEntry[] menuEntries = client.getMenuEntries();
			MenuEntry correctEgg = null;
			entries.clear();

			for (MenuEntry entry : menuEntries)
			{
				if (entry.getOption().equals("Take-" + currentCall[1]))
				{
					correctEgg = entry;
				}
			}
			if (correctEgg != null)
			{
				entries.add(correctEgg);
				client.setMenuEntries(entries.toArray(new MenuEntry[entries.size()]));
			}
		}

	}

	@Subscribe
	public void onMenuOptionClicked(MenuOptionClicked event)
	{
		String target = event.getMenuTarget();

		if (config.tagging() && (event.getMenuTarget().contains("Penance Ranger") || event.getMenuTarget().contains("Penance Fighter")))
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
		if (config.healerMenuOption() && target.contains("Crate") && target.contains("<col=ff9040>Chisel") && target.contains("->"))
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
		if (!config.deathTimeBoxes() || !config.defTimer())
		{
			removeCounter();
		}
	}

	@Subscribe
	public void onChatMessage(ChatMessage event)
	{
		if (event.getType() != ChatMessageType.GAMEMESSAGE)
		{
			return;
		}

		if (event.getMessage().startsWith("All of the Penance") && gameTime != null && inGameBit != 0)
		{
			String[] message = event.getMessage().split(" ");
			final int waveSeconds = (int)gameTime.getTimeInSeconds(true);

			if (config.deathTimeBoxes())
			{
				switch (message[4])
				{
					case "Healers":
						monsterDeathInfoBox[0] = new BAMonsterBox(healerImage, this, waveSeconds, message[4], Color.green);
						infoBoxManager.addInfoBox(monsterDeathInfoBox[0]);
						break;
					case "Runners":
						monsterDeathInfoBox[1] = new BAMonsterBox(runnerImage, this, waveSeconds, message[4], Color.blue);
						infoBoxManager.addInfoBox(monsterDeathInfoBox[1]);
						break;
					case "Fighters":
						monsterDeathInfoBox[2] = new BAMonsterBox(fighterImage, this, waveSeconds, message[4], Color.red);
						infoBoxManager.addInfoBox(monsterDeathInfoBox[2]);
						break;
					case "Rangers":
						monsterDeathInfoBox[3] = new BAMonsterBox(rangerImage, this, waveSeconds, message[4], Color.red);
						infoBoxManager.addInfoBox(monsterDeathInfoBox[3]);
						break;
				}
			}
			if (config.monsterDeathTimeChat())
			{
				final MessageNode node = event.getMessageNode();
				final String nodeValue = Text.removeTags(node.getValue());
				node.setValue(nodeValue + " (<col=ff0000>" + gameTime.getTimeInSeconds(true) + "s<col=ffffff>)");
				chatMessageManager.update(node);
			}
		}

		if (event.getMessage().startsWith("---- Wave:"))
		{
			String[] message = event.getMessage().split(" ");
			currentWave = message[2];

			if (currentWave.equals(START_WAVE))
			{
				gameTime = new GameTimer();
			}
			else if (gameTime != null)
			{
				gameTime.setWaveStartTime();
			}
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
		if (counter != null)
		{
			infoBoxManager.removeInfoBox(counter);
			counter = null;
		}
		for (int i = 0;i < monsterDeathInfoBox.length;i++)
		{
			if (monsterDeathInfoBox[i] != null)
			{
				infoBoxManager.removeInfoBox(monsterDeathInfoBox[i]);
				monsterDeathInfoBox[i] = null;
			}
		}
	}

	private void remove(String option, String target, boolean strict)
	{
		MenuEntry[] entries = client.getMenuEntries();
		int idx = searchIndex(entries, option, target, strict);
		if (idx >= 0 && entries[idx] != null)
		{
			entries = ArrayUtils.removeElement(entries, entries[idx]);
			client.setMenuEntries(entries);
		}
	}

	private void remove(String[] options, String target, boolean strict)
	{
		MenuEntry[] entries = client.getMenuEntries();
		for (int i = 0; i < options.length; i++)
		{
			int idx = searchIndex(entries, options[i], target, strict);
			if (idx >= 0 && entries[idx] != null)
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