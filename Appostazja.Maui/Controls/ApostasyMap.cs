using Microsoft.Maui.Maps;

namespace Appostazja.Maui.Controls;

public class ApostasyMap : Microsoft.Maui.Controls.Maps.Map
{
    public ApostasyMap()
    {
        MoveToRegion(new MapSpan(new Location(51.91943, 19.1451359), 19, 19));
    }
}