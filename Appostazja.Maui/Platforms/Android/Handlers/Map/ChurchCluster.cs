using Android.Content;
using Android.Gms.Maps;
using Android.Gms.Maps.Utils.Clustering;
using Android.Gms.Maps.Utils.Clustering.View;
using System;
using System.Collections.Generic;
using System.Linq;
using System.Text;
using System.Threading.Tasks;

namespace Appostazja.Maui.Platforms.Android.Handlers.Map
{
    public class ChurchCluster : DefaultClusterRenderer
    {
        public ChurchCluster(Context? context, GoogleMap? map, ClusterManager? clusterManager) : base(context, map, clusterManager)
        {
            MinClusterSize = 5;
        }

        protected override bool ShouldRenderAsCluster(ICluster cluster)
        {
            return cluster.Size > 5;
        }
    }
}
