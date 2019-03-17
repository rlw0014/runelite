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
package net.runelite.client.plugins.furnace;
import javax.inject.Inject;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import net.runelite.api.Client;
import net.runelite.api.NPC;

import net.runelite.api.events.AnimationChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetID;
import net.runelite.api.widgets.WidgetInfo;

import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.overlay.OverlayManager;


@Slf4j
@PluginDescriptor(
	name = "Furnace",
	description = "Furnace",
	tags = {"minigame"}
)
public class FurnacePlugin extends Plugin
{
	private final int magicPrayer = 127;
	private final int magicHiddenPrayer = 147;
	private final int rangePrayer = 128;
	private final int rangeHiddenPrayer = 148;

	private final int mageAnimation = 7592;
	private final int rangeAnimation = 7593;

	@Inject
	private OverlayManager overlayManager;

	@Inject
	private FurnaceOverlay overlay;

	@Inject
	private Client client;

	@Setter
	@Getter
	private Widget prayer;

	@Override
	protected void startUp() throws Exception
	{
		overlayManager.add(overlay);
	}

	@Override
	protected void shutDown() throws Exception
	{
		overlayManager.remove(overlay);
	}

	@Subscribe
	public void onAnimationChanged(final AnimationChanged event)
	{

		if (event.getActor() instanceof NPC)
		{
			final NPC npc = (NPC) event.getActor();
			int id = npc.getId();

			if(id == 7700 || id == 7704)
			{
				Widget rangeWidget = client.getWidget(WidgetInfo.PRAYER_PROTECT_FROM_MISSILES);
				Widget mageWidget = client.getWidget(WidgetInfo.PRAYER_PROTECT_FROM_MAGIC);

				if(npc.getAnimation() == mageAnimation)
				{
					prayer = mageWidget;
				}
				if(npc.getAnimation() == rangeAnimation)
				{
					prayer = rangeWidget;
				}
			}

		}
	}
}