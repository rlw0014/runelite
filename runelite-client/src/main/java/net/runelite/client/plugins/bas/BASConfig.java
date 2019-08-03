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
package net.runelite.client.plugins.bas;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;

@ConfigGroup("BAS")
public interface BASConfig extends Config
{
	@ConfigItem(
			keyName = "autoUpdateQueue",
			name = "Queue Auto-updater",
			description = "Automatically updates the BAS Queue",
			position = 1
	)
	default boolean autoUpdateQueue()
	{
		return true;
	}

	@ConfigItem(
			keyName = "premNotifier",
			name = "Premium Notifier",
			description = "Notify if a Premium customer comes online/offline"
			,
			position = 2
	)
	default boolean premNotifier()
	{
		return true;
	}

	@ConfigItem(
			keyName = "markCustomerOptions",
			name = "Mark Customer Options",
			description = "Adds options to mark customers"
			,
			position = 3
	)
	default boolean markCustomerOptions()
	{
		return false;
	}


	@ConfigItem(
			keyName = "addToQueue",
			name = "Shift add to queue options",
			description = "Hold shift to view more options that allow adding customers directly to the queue",
			position = 4
	)
	default boolean addToQueue()
	{
		return false;
	}

	@ConfigItem(
			keyName = "getNextCustomer",
			name = "Get next customer option",
			description = "Button to announce the next customer (replaces Clan Setup button)",
			position = 5
	)
	default boolean getNextCustomer()
	{
		return false;
	}

	@ConfigItem(
			keyName = "torsoOptions",
			name = "Torso Options",
			description = "Show options to add Torso when \"Shift add to queue options\" is enabled",
			position = 6
	)
	default boolean torsoOptions()
	{
		return false;
	}

	@ConfigItem(
			keyName = "hatOptions",
			name = "Hat Options",
			description = "Show options to add Hat when \"Shift add to queue options\" is enabled",
			position = 7
	)
	default boolean hatOptions()
	{
		return false;
	}

	@ConfigItem(
			keyName = "qkOptions",
			name = "Queen Kill Options",
			description = "Show options to add Queen Kill when \"Shift add to queue options\" is enabled",
			position = 8
	)
	default boolean qkOptions()
	{
		return false;
	}

	@ConfigItem(
			keyName = "OneROptions",
			name = "One Round Options",
			description = "Show options to add One Round - Points when \"Shift add to queue options\" is enabled",
			position = 9
	)
	default boolean OneROptions()
	{
		return false;
	}

	@ConfigItem(
			keyName = "Lvl5Options",
			name = "Level 5 Roles Options",
			description = "Show options to add Level 5 Roles when \"Shift add to queue options\" is enabled",
			position = 10
	)
	default boolean Lvl5Options()
	{
		return false;
	}

	@ConfigItem(
			keyName = "queueName",
			name = "Queue sheet name",
			description = "The name that you would like the queue to recognise you as. If not set it will use the currently logged in username.",
			position = 11
	)
	default String queueName()
	{
		return "";
	}


}
