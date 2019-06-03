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

import net.runelite.api.Item;
import net.runelite.api.Prayer;
import net.runelite.api.SoundEffectID;
import net.runelite.api.Tile;
import net.runelite.api.kit.KitType;
import net.runelite.client.eventbus.Subscribe;
import com.google.inject.Provides;
import java.awt.event.KeyEvent;
import java.awt.image.BufferedImage;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.inject.Inject;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Actor;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import static net.runelite.api.Constants.CHUNK_SIZE;
import net.runelite.api.ItemID;
import net.runelite.api.MenuEntry;
import net.runelite.api.NPC;
import net.runelite.api.NpcID;
import net.runelite.api.Varbits;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.ConfigChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.HitsplatApplied;
import net.runelite.api.events.InteractingChanged;
import net.runelite.api.events.MenuEntryAdded;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.api.events.NpcDespawned;
import net.runelite.api.events.NpcSpawned;
import net.runelite.api.events.VarbitChanged;
import net.runelite.api.events.WidgetLoaded;
import net.runelite.api.events.WidgetHiddenChanged;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetID;
import net.runelite.api.widgets.WidgetInfo;
import net.runelite.client.chat.ChatMessageManager;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.game.ItemManager;
import net.runelite.client.input.KeyListener;
import net.runelite.client.input.KeyManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.ui.overlay.infobox.InfoBoxManager;
import net.runelite.client.util.Text;

@Slf4j
@PluginDescriptor(
	name = "BA Tools",
	description = "Custom tools for Barbarian Assault",
	tags = {"minigame", "overlay", "timer"}
)
public class BAToolsPlugin extends Plugin implements KeyListener
{
	int inGameBit = 0;
	int tickNum;
	int pastCall = 0;
	private int currentWave = 1;
	private int lastHealer;
	private static final int BA_WAVE_NUM_INDEX = 2;
	private final List<MenuEntry> entries = new ArrayList<>();
	private HashMap<Integer, Instant> foodPressed = new HashMap<>();
	private CycleCounter counter;

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
		wave_start = Instant.now();
		foodPressed.clear();
		keyManager.registerKeyListener(this);
		lastHealer = 0;
	}

	@Override
	protected void shutDown() throws Exception
	{
		removeCounter();
		inGameBit = 0;
		client.setInventoryDragDelay(5);
		keyManager.unregisterKeyListener(this);
		shiftDown = false;
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

		if(config.prayerMetronome() && isAnyPrayerActive())
		{
			for(int i = 0; i < config.prayerMetronomeVolume(); i++)
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
		String option = Text.removeTags(event.getOption()).toLowerCase();
		String target = Text.removeTags(event.getTarget()).toLowerCase();

		//Incorrect call remover
		if (config.calls() && getWidget() != null && event.getTarget().endsWith("horn") && inGameBit==1)
		{
			MenuEntry[] menuEntries = client.getMenuEntries();
			Widget callWidget = getWidget();
			String call = Calls.getOption(callWidget.getText());
			MenuEntry correctCall = null;

			entries.clear();
			for (MenuEntry entry : menuEntries)
			{
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
		else if (config.calls() && event.getTarget().endsWith("horn") && inGameBit==1)
		{
			entries.clear();
			client.setMenuEntries(entries.toArray(new MenuEntry[entries.size()]));
		}

		//Ladder swap
		if (config.swapLadder() && option.equals("climb-down") && target.equals("ladder"))
		{
			swap("quick-start", option, target, true);
		}

		//Ctrl Healer
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
										(entry.getOption().equals("Use")||entry.getOption().equals("Cancel"))
						)
				)
				{
					correctHealer = entry;
				}
				else
				{
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

		//Target menu times/colour
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