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

import java.awt.Color;
import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;

@ConfigGroup("BATools")
public interface BAToolsConfig extends Config
{
	@ConfigItem(
		   keyName = "showTimer",
		   name = "Show call change timer",
		   description = "Show time to next call change",
		   hidden = true
	)
	default boolean showTimer()
	{
		return true;
	}

	@ConfigItem(
		   keyName = "waveTimes",
		   name = "Show wave and game duration",
		   description = "Displays wave and game duration",
		   hidden = true
	)
	default boolean waveTimes()
	{
		return true;
	}

	@ConfigItem(
		keyName = "defTimer",
		name = "Defender Tick Timer",
		description = "Shows the current cycle tick of runners."
	)
	default boolean defTimer()
	{
		return false;
	}

	@ConfigItem(
		keyName = "calls",
		name = "Remove Incorrect Calls",
		description = "Remove incorrect calls."
	)
	default boolean calls()
	{
		return false;
	}

	@ConfigItem(
		keyName = "swapLadder",
		name = "Swap ladder option",
		description = "Swap Climb-down with Quick-start in the wave lobbies"
	)
	default boolean swapLadder()
	{
		return true;
	}

	@ConfigItem(
		keyName = "healerMenuOption",
		name = "Healer menu options",
		description = "Shows time since last food placed on healer"
	)
	default boolean healerMenuOption()
	{
		return false;
	}

	@ConfigItem(
		   keyName = "healerCodes",
		   name = "Healer Codes",
		   description = "Overlay to show healer codes"
	)
	default boolean healerCodes()
	{
		return false;
	}

	@ConfigItem(
		keyName = "antiDrag",
		name = "Anti Drag",
		description = "asd"
	)
	default boolean antiDrag()
	{
		return false;
	}

	@ConfigItem(
		keyName = "antiDragDelay",
		name = "Anti Drag Delay",
		description = "Similar to antidrag plugin but does not require shift to be help down"
	)
	default int antiDragDelay()
	{
		return 5;
	}

	@ConfigItem(
		keyName = "eggBoi",
		name = "Collector helper",
		description = "asd"
	)
	default boolean eggBoi()
	{
		return false;
	}

	@ConfigItem(
		keyName = "osHelp",
		name = "Shift OS",
		description = "Hold shift to only pick up correct eggs"
	)
	default boolean osHelp()
	{
		return false;
	}

	@ConfigItem(
		keyName = "prayerMetronome",
		name = "Prayer Metronome",
		description = "Similar to metronome plugin but only activates when a prayer is active"
	)
	default boolean prayerMetronome()
	{
		return false;
	}

	@ConfigItem(
		keyName = "prayerMetronomeVolume",
		name = "Prayer Metronome Volume",
		description = "asd"
	)
	default int prayerMetronomeVolume()
	{
		return 1;
	}

	@ConfigItem(
		keyName = "attackStyles",
		name = "Attack Styles",
		description = "Remove incorrect attack styles for hally/claws and if shift held down = all attack options are removed to help walk to the correct tiles"
	)
	default boolean attackStyles()
	{
		return false;
	}

	@ConfigItem(
		keyName = "removeBA",
		name = "*Barbarian Assault Helper*",
		description = "Remove unnecessary menu options in Barbarian Assault depending on role<br>Examples: Remove attack options when not attacker<br>Remove take options when not collector"
	)
	default boolean removeBA()
	{
		return true;
	}

	@ConfigItem(
		keyName = "removeWrongEggs",
		name = "Remove wrong eggs - *Barbarian Assault Helper*",
		description = "Remove unnecessary menu options in Barbarian Assault depending on role<br>Examples: Remove attack options when not attacker<br>Remove take options when not collector"
	)
	default boolean removeWrongEggs()
	{
		return false;
	}

	@ConfigItem(
		keyName = "removeWrongHealFood",
		name = "Remove wrong Heal Food - *Barbarian Assault Helper*",
		description = "Remove unnecessary menu options in Barbarian Assault depending on role<br>Examples: Remove attack options when not attacker<br>Remove take options when not collector"
	)
	default boolean removeHealWrongFood()
	{
		return false;
	}

	@ConfigItem(
		keyName = "removePenanceCave",
		name = "Remove 'Block' Penance Cave",
		description = "Removes unnecessary menu option, however Moon wanted it back"
	)
	default boolean removePenanceCave()
	{
		return false;
	}

	@ConfigItem(
		keyName = "tagging",
		name = "Attack Tags",
		description = "Highlights the menu entry of an attacker/ranger that has not been tagged."
	)
	default boolean tagging()
	{
		return false;
	}

	@ConfigItem(
		keyName = "ctrlHealer",
		name = "Control Healer",
		description = "Hold ctrl to put last healer clicked on top"
	)
	default boolean ctrlHealer()
	{
		return false;
	}

	@ConfigItem(
			keyName = "monsterDeathTimeChat",
			name = "Monster Death Time in Chat",
			description = "Shows time in seconds when a specific monster dies"
	)
	default boolean monsterDeathTimeChat()
	{
		return false;
	}

	@ConfigItem(
			keyName = "deathTimeBoxes",
			name = "Monster Death Boxes",
			description = "Shows info boxes when a specific monster dies and time"
	)
	default boolean deathTimeBoxes()
	{
		return false;
	}
	@ConfigItem(
		keyName = "healerGreenColor",
		name = "Healer green menu option",
		description = "Toggle healer's menu option as green"
	)
    	default boolean healerGreenColor()
    	{
	   	return false;
    	}

	@ConfigItem(
		  keyName = "shiftWalkHere",
		  name = "Shift-Click Walk here",
		  description = "Make Walk here the left-click option when holding shift"
	)
	default boolean shiftWalkHere()
	{
	   return false;
	}

	@ConfigItem(
		  keyName = "swapCollectorBag",
		  name = "Swap left click collector bag to Empty",
		  description = "Make empty the left-click option on collector bag 'Empty'"
	)
	default boolean swapCollectorBag()
	{
	   return false;
	}

	@ConfigItem(
		  keyName = "swapDestroyEggs",
		  name = "Swap left click destroy eggs in BA",
		  description = "Make destroy the left-click option for collector eggs"
	)
	default boolean swapDestroyEggs()
	{
	   return false;
	}

	@ConfigItem(
		  keyName = "showEggCountOverlay",
		  name = "Overlay of eggs counted",
		  description = "Display current egg count as collector"
	)
	default boolean showEggCountOverlay() { return false; }

	@ConfigItem(
		  keyName = "showHpCountOverlay",
		  name = "Overlay of Hp counted",
		  description = "Display current healed count as healer"
	)
	default boolean showHpCountOverlay() { return false; }

	@ConfigItem(
		  keyName = "highlightCollectorEggs",
		  name = "Highlight collector eggs",
		  description = "Highlight called egg colors"
	)
	default boolean highlightCollectorEggs() { return false; }

	@ConfigItem(
		  keyName = "showTotalRewards",
		  name = "Summarize total reward points",
		  description = "Displays total eggs/healed hp and missed attacks/lost runners"
	)
	default boolean showTotalRewards(){ return false; }

	@ConfigItem(
		  keyName = "showSummaryOfPoints",
		  name = "Display summary of advanced points",
		  description = "Gives summary of advanced points breakdown in chat log"
	)
	default boolean showSummaryOfPoints() { return false; }

	@ConfigItem(
		  keyName = "wrongPoisonFoodTextColor",
		  name = "Change healer wrong poison pack color",
		  description = "Change healer wrong poison pack color"
	)
	default Color wrongPoisonFoodTextColor() { return Color.BLACK;}

	@ConfigItem(
		  keyName = "highlightItems",
		  name = "Highlight called poison/bait",
		  description = "Highlights the poison or bait that was called by your teammate"
	)
	default boolean highlightItems()
	{
	   return false;
	}

	@ConfigItem(
		  keyName = "highlightColor",
		  name = "Highlight color",
		  description = "Configures the color to highlight the called poison/bait"
	)
	default Color highlightColor()
	{
	   return Color.GREEN;
	}

	@ConfigItem(
		   keyName = "testing",
		   name = "testing",
		   description = "testing"
	)
	default boolean testing()
	{
		return false;
	}
}