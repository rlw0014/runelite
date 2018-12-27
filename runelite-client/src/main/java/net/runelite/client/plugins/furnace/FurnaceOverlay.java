package net.runelite.client.plugins.furnace;

import static java.awt.Color.CYAN;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.widgets.Widget;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;


public class FurnaceOverlay extends Overlay
{
	private final Client client;
	private final FurnacePlugin plugin;

	@Inject
	private FurnaceOverlay(Client client, FurnacePlugin plugin)
	{
		this.client = client;
		this.plugin = plugin;
		setPosition(OverlayPosition.DYNAMIC);
		setLayer(OverlayLayer.ALWAYS_ON_TOP);
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		Widget widget;

		if (plugin.getPrayer() != null)
		{
			widget = plugin.getPrayer();

			Rectangle childBounds = widget.getBounds();
			graphics.setColor(CYAN);
			graphics.draw(childBounds);
		}

		return null;
	}
}
