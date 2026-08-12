using Appostazja.Maui.Controls.Map;
using Appostazja.Maui.Models;

namespace Appostazja.Maui.Services;

public interface IDataService
{
    Task<MapResponseModel?> GetGeoJsonAsync();
}