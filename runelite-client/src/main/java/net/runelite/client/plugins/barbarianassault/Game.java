package net.runelite.client.plugins.barbarianassault;

import lombok.Getter;
import net.runelite.api.Client;
import net.runelite.api.events.ChatMessage;
import net.runelite.client.chat.ChatColorType;
import net.runelite.client.chat.ChatMessageBuilder;
import net.runelite.client.eventbus.Subscribe;

import java.awt.*;
import java.util.ArrayList;

@Getter
public class Game
{
    private Client client;
    private String currentWave;
    private ArrayList<Wave> Waves = new ArrayList<>();
    private String[] totalDescriptions = {
            "A: ",
            "; D: ",
            "; C: ",
            "; Vial: ",
            "; H packs: ",
            "; Total: "};
    private String[] otherPointsDescriptions = {
            " A: ",
            "; D: ",
            "; C: ",
            "; H: "
    };
    private int[] totalPoints = new int[6];
    private int[] totalAmounts = new int[6];
    private int[] otherRolesPoints = new int[4];

    Game(Client client)
    {
        this.client = client;
    }
    Game(Client client, ArrayList<Wave> Waves)
    {
        this.client = client;
        this.Waves = Waves;
    }
    @Subscribe
    public void onChatMessage(ChatMessage chatMessage)
    {
        if (chatMessage.getMessage().startsWith("---- Wave:"))
        {
            String[] tempMessage = chatMessage.getMessage().split(" ");
            currentWave = tempMessage[2];
            String[] temp = currentWave.split(" ");
        }
        if (currentWave.equals("1"))
        {
            Waves = null;
            totalPoints = new int[6];
            totalAmounts = new int[6];
        }
    }
    ChatMessageBuilder getGameSummary()
    {
        int[] amountsList;
        int[] pointsList;
        int[] otherRolesPointsList;
        ChatMessageBuilder message = new ChatMessageBuilder();
        message.append("Round points: ");
        for (Wave w : Waves)
        {
            amountsList = w.getWaveAmounts();
            pointsList = w.getWavePoints();
            otherRolesPointsList = w.getOtherRolesPointsList();
            for (int j = 0; j < totalAmounts.length; j++) {
                totalAmounts[j] += amountsList[j];
            }
            for (int k = 0; k < totalPoints.length; k++) {
                totalPoints[k] += pointsList[k];
            }
            for (int z = 0; z < otherRolesPoints.length; z++) {
                otherRolesPoints[z] += otherRolesPointsList[z];
            }
        }
        for (int i = 0; i < otherRolesPoints.length; i++)
        {
            otherRolesPoints[i] += 80;
        }
        totalAmounts[5] += 80;
        for (int i = 0; i < totalDescriptions.length; i++)
        {
            if (i != 4)
            {
                message.append(totalDescriptions[i]);
                message.append(String.valueOf(totalAmounts[i]));
                message.append("(");
                if (totalPoints[i] < 0)
                {
                    message.append(Color.RED, String.valueOf(totalPoints[i]));
                }
                else if (totalPoints[i] > 0)
                {
                    message.append(Color.BLUE, String.valueOf(totalPoints[i]));
                }
                else
                {
                    message.append(String.valueOf(totalPoints[i]));
                }
                message.append(")");
            }
        }
        message.append(System.getProperty("line.separator"));
        message.append("All roles points this game: ");
        for(int i = 0; i < otherPointsDescriptions.length; i++)
        {
            message.append(otherPointsDescriptions[i]);
            message.append(String.valueOf(otherRolesPoints[i]));
        }
        return message;
    }
}
