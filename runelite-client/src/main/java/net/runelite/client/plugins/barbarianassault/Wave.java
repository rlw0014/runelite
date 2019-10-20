package net.runelite.client.plugins.barbarianassault;

import lombok.Getter;
import net.runelite.api.Client;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetID;
import net.runelite.client.chat.ChatMessageBuilder;
import java.awt.*;


@Getter
class Wave
{
    private Client client;

    //[0] BA_FAILED_ATTACKER_ATTACKS
    //[1] BA_RUNNERS_PASSED
    //[2] BA_EGGS_COLLECTED
    //[3] BA_HITPOINTS_REPLENISHED
    //[4] BA_WRONG_POISON_PACKS
    //[5] BA_HONOUR_POINTS_REWARD

    final int[] childIDsOfWidgets = new int[]{22, 14, 21, 19, 20, 49};

    //base
    //[0] BA_BASE_POINTS,
    //att
    //[1] BA_FAILED_ATTACKER_ATTACKS_POINTS,
    //[2] BA_RANGERS_KILLED,
    //[3] BA_FIGHTERS_KILLED,
    //def
    //[4] BA_RUNNERS_PASSED_POINTS,
    //[5] BA_RUNNERS_KILLED,
    //coll
    //[6] BA_EGGS_COLLECTED_POINTS,
    //heal
    //[7] BA_HEALERS_KILLED,
    //[8] BA_HITPOINTS_REPLENISHED_POINTS,
    //[9] BA_WRONG_POISON_PACKS_POINTS

    final int[] childIDsOfPointsWidgets = new int[]{33, 32, 25, 26, 24, 28, 31, 27, 29, 30};

    private int[] waveAmounts = new int[6];
    private int[] allPointsList = new int[10];
    private int[] wavePoints = new int[6];
    private int[] otherRolesPointsList = new int[4];
    private String[] descriptions = {
            " A: ",
            "; D: ",
            "; C: ",
            "; Vial: ",
            "; H packs: ",
            "; Total: "};

    private String[] otherPointsDescriptions = {
            " A: ",
            " D: ",
            " C: ",
            " H: "
    };
    Wave(Client client)
    {
        this.client = client;
    }
    void setWaveAmounts(int[] amounts)
    {
        for (int i = 0; i < amounts.length; i++)
        {
            waveAmounts[i] = amounts[i];
        }
    }

    void setWavePoints(int[] points, int[] otherRolesPoints)
    {
        for (int i = 0; i < points.length; i++)
        {
            wavePoints[i] = points[i];
        }
        for (int i = 0; i < otherRolesPoints.length; i++)
        {
            otherRolesPointsList[i] = otherRolesPoints[i];
        }
    }
    void setWaveAmounts()
    {
        for (int i = 0; i < childIDsOfWidgets.length; i++)
        {
            Widget w = client.getWidget(WidgetID.BA_REWARD_GROUP_ID, childIDsOfWidgets[i]);
            if (w != null)
            {
                waveAmounts[i] = Integer.parseInt(w.getText());
            }
        }
    }
    void setWavePoints()
    {
        for (int i = 0; i < childIDsOfPointsWidgets.length; i++)
        {
            Widget w = client.getWidget(WidgetID.BA_REWARD_GROUP_ID, childIDsOfPointsWidgets[i]);
            allPointsList[i] = Integer.parseInt(w.getText());
            switch (i)
            {
                case 1:
                    wavePoints[0] += allPointsList[i];
                    break;
                case 4:
                    wavePoints[1] += allPointsList[i];
                    break;
                case 6:
                    wavePoints[2] += allPointsList[i];
                    break;
                case 8:
                    wavePoints[3] += allPointsList[i];
                    break;
                default:
                    break;
            }
        }
        wavePoints[5] = 0;
        for (int i = 0; i < wavePoints.length-1; i++)
        {
            wavePoints[5] += wavePoints[i];
        }
        for (int i = 0; i < childIDsOfPointsWidgets.length; i++)
        {
            Widget w = client.getWidget(WidgetID.BA_REWARD_GROUP_ID, childIDsOfPointsWidgets[i]);
            switch (i)
            {
                case 0:
                    otherRolesPointsList[0] += Integer.parseInt(w.getText());
                    otherRolesPointsList[1] += Integer.parseInt(w.getText());
                    otherRolesPointsList[2] += Integer.parseInt(w.getText());
                    otherRolesPointsList[3] += Integer.parseInt(w.getText());
                    break;
                case 1:
                case 2:
                case 3:
                    otherRolesPointsList[0] += Integer.parseInt(w.getText());
                    break;
                case 4:
                case 5:
                    otherRolesPointsList[1] += Integer.parseInt(w.getText());
                    break;
                case 6:
                    otherRolesPointsList[2] += Integer.parseInt(w.getText());
                    break;
                case 7:
                case 8:
                case 9:
                    otherRolesPointsList[3] += Integer.parseInt(w.getText());
                    break;
                default:
                    break;
            }
        }
    }
    ChatMessageBuilder getWaveSummary()
    {
        ChatMessageBuilder message = new ChatMessageBuilder();
        message.append("Wave points:");
        for(int i = 0; i < descriptions.length; i++)
        {
            if (i != 4)
            {
                message.append(descriptions[i]);
                message.append(String.valueOf(waveAmounts[i]));
                message.append("(");
                if (wavePoints[i] < 0)
                {
                    message.append(Color.RED, String.valueOf(wavePoints[i]));
                }
                else if (wavePoints[i] > 0)
                {
                    message.append(Color.BLUE, String.valueOf(wavePoints[i]));
                }
                else
                {
                    message.append(String.valueOf(wavePoints[i]));
                }
                message.append(")");
            }
        }
        message.append(System.getProperty("line.separator"));
        message.append("All roles points this wave: ");
        for(int i = 0; i < otherPointsDescriptions.length; i++)
        {
            message.append(otherPointsDescriptions[i]);
            message.append(String.valueOf(otherRolesPointsList[i]));
        }
        return message;
    }
}
