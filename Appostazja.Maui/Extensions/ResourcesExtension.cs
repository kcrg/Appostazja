using System;
using System.Collections.Generic;
using System.Linq;
using System.Text;
using System.Threading.Tasks;

namespace Appostazja.Maui.Extensions
{
    public static class ResourcesExtension
    {
        public static Color GetStaticResource(string resourceKey)
        {
            if (Application.Current?.Resources.TryGetValue(resourceKey, out object value) == true && value is Color color)
            {
                return color;
            }

            return Color.FromRgba("#000000");
        }
    }
}
