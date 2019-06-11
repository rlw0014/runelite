/*
 * Copyright (c) 2018, Woox <https://github.com/wooxsolo>
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

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.Rectangle;
import java.awt.Stroke;
import java.awt.image.BufferedImage;
import java.util.Map;
import lombok.Getter;
import lombok.Setter;
import net.runelite.api.GameState;
import static net.runelite.api.MenuAction.RUNELITE_OVERLAY_CONFIG;
import net.runelite.api.NPCComposition;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.Perspective;
import net.runelite.api.Player;
import net.runelite.api.Point;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetInfo;
import net.runelite.api.widgets.WidgetItem;
import net.runelite.client.game.ItemManager;
import net.runelite.client.plugins.barbarianassault.BarbarianAssaultPlugin;
import static net.runelite.client.ui.overlay.OverlayManager.OPTION_CONFIGURE;
import net.runelite.client.ui.overlay.OverlayMenuEntry;
import net.runelite.client.ui.overlay.OverlayUtil;
import net.runelite.client.ui.overlay.Overlay;
import java.time.Duration;
import java.time.Instant;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.util.ImageUtil;

@Slf4j

public class BAToolsOverlay extends Overlay
{
	private static final Color RED = new Color(221, 44, 0);
	private static final Color GREEN = new Color(0, 200, 83);
	private static final Color ORANGE = new Color(255, 109, 0);
	private static final Color YELLOW = new Color(255, 214, 0);
	private static final Color CYAN = new Color(0, 184, 212);
	private static final Color BLUE = new Color(41, 98, 255);
	private static final Color DEEP_PURPLE = new Color(98, 0, 234);
	private static final Color PURPLE = new Color(170, 0, 255);
	private static final Color GRAY = new Color(158, 158, 158);

	private final BAToolsConfig config;
	private Client client;
	private final ItemManager itemManager;
	private BAToolsPlugin plugin;
	private BarbarianAssaultPlugin plugin2;
	@Getter
	@Setter
    	private Round currentRound;

	private static final int MAX_EGG_DISTANCE = 2500;
	private static final int OFFSET_Z = 20;

	@Inject
	public BAToolsOverlay(Client client, ItemManager itemManager, BAToolsPlugin plugin, BAToolsConfig config)
	{
		setPosition(OverlayPosition.DYNAMIC);
		setLayer(OverlayLayer.ABOVE_SCENE);
		this.config = config;
		this.client = client;
		this.itemManager = itemManager;
		this.plugin = plugin;
		getMenuEntries().add(new OverlayMenuEntry(RUNELITE_OVERLAY_CONFIG, OPTION_CONFIGURE, "B.A. overlay"));
	}


	@Override
	public Dimension render(Graphics2D graphics)
	{
	    if (client.getGameState() != GameState.LOGGED_IN || currentRound == null)
	    {
		   return null;
	    }

	    Role role = currentRound.getRoundRole();
	    if (role == null)
	    {
		   return null;
	    }

	    Widget roleText = client.getWidget(role.getRoleText());
	    Widget roleSprite = client.getWidget(role.getRoleSprite());

	    if (config.showTimer() && roleText != null && roleSprite != null)
	    {
		   if (config.showEggCountOverlay() && role.equals(Role.COLLECTOR))
		   {
			  roleText.setText(String.format("(%d) 00:%02d", plugin.getCollectedEggCount(), currentRound.getTimeToChange()));
		   }
		   else if (config.showHpCountOverlay() && role.equals(Role.HEALER))
		   {
			  roleText.setText(String.format("(%d) 00:%02d", plugin.getHpHealed(), currentRound.getTimeToChange()));
		   }
		   else
		   {
			  roleText.setText(String.format("00:%02d", currentRound.getTimeToChange()));
		   }
		   //Rectangle spriteBounds = roleSprite.getBounds();
		   //roleSprite.setHidden(true);
		   //graphics.drawImage(plugin.getClockImage(), spriteBounds.x, spriteBounds.y, null);
	    }

	    if (role == Role.COLLECTOR && config.highlightCollectorEggs())
	    {
		   String heardCall = plugin.getCollectorHeardCall();
		   Color highlightColor = BAToolsPlugin.getEggColor(heardCall);
		   Map<WorldPoint, Integer> calledEggMap = plugin.getCalledEggMap();
		   Map<WorldPoint, Integer> yellowEggMap = plugin.getYellowEggs();

		   if (calledEggMap != null) {
			  renderEggLocations(graphics, calledEggMap, highlightColor);
		   }

		   // Always show yellow eggs
		   renderEggLocations(graphics, yellowEggMap, Color.YELLOW);
	    }
	    Widget inventory = client.getWidget(WidgetInfo.INVENTORY);

	    if (config.highlightItems() && inventory != null && !inventory.isHidden() && ((role == Role.DEFENDER || role == Role.HEALER)))
	    {
		   int listenItemId = plugin.getListenItemId(role.getListen());

		   if (listenItemId != -1) {
			  Color color = config.highlightColor();
			  BufferedImage highlight = ImageUtil.fillImage(itemManager.getImage(listenItemId), new Color(color.getRed(), color.getGreen(), color.getBlue(), 150));

			  for (WidgetItem item : inventory.getWidgetItems())
			  {
				 if (item.getId() == listenItemId)
				 {
					OverlayUtil.renderImageLocation(graphics, item.getCanvasLocation(), highlight);
				 }
			  }
		   }
	    }

		if(config.healerCodes())
		{
		    for (Healer healer : plugin.getHealers().values()) {
			   NPCComposition composition = healer.getNpc().getComposition();
			   Color color = composition.getCombatLevel() > 1 ? YELLOW : ORANGE;
			   if (composition.getConfigs() != null) {
				  NPCComposition transformedComposition = composition.transform();
				  if (transformedComposition == null) {
					 color = GRAY;
				  } else {
					 composition = transformedComposition;
				  }
			   }
			   int timeLeft = healer.getLastFoodTime() - (int) Duration.between(plugin.getWave_start(), Instant.now()).getSeconds();
			   timeLeft = timeLeft < 1 ? 0 : timeLeft;

			   if (healer.getFoodRemaining() > 1) {
				  color = GREEN;
			   } else if (healer.getFoodRemaining() == 1) {
				  if (timeLeft > 0) {
					 color = RED;
				  } else {
					 color = GREEN;
				  }
			   } else {
				  continue;
			   }

			   String text = String.format("%d  %d",
					 healer.getFoodRemaining(),
					 timeLeft);


			   OverlayUtil.renderActorOverlay(graphics, healer.getNpc(), text, color);
		    }
		}
		return null;
	}
    	private void renderEggLocations(Graphics2D graphics, Map<WorldPoint, Integer> eggMap, Color color)
	{
	    Player player = client.getLocalPlayer();
	    if (player == null)
	    {
		  return;
	    }

	    final Stroke originalStroke = graphics.getStroke();

	    for (WorldPoint worldPoint : eggMap.keySet())
	    {
	        LocalPoint groundPoint = LocalPoint.fromWorld(client, worldPoint);

	        if (groundPoint == null)
	        {
	            continue;
	        }
	        if (player.getLocalLocation().distanceTo(groundPoint) > MAX_EGG_DISTANCE) {
	            continue;
	        }

	        Polygon poly = Perspective.getCanvasTilePoly(client, groundPoint);

	        if (poly == null)
	        {
	            continue;
	        }

	        int quantity = eggMap.get(worldPoint);
	        String quantityText = "x" + quantity;
	        Point textPoint = Perspective.getCanvasTextLocation(client, graphics, groundPoint, quantityText, OFFSET_Z);
	        graphics.setColor(color);
	        graphics.setStroke(new BasicStroke(2));
	        graphics.drawPolygon(poly);
	        OverlayUtil.renderTextLocation(graphics, textPoint, quantityText, Color.WHITE);
	    }

	    graphics.setStroke(originalStroke);
	}
}