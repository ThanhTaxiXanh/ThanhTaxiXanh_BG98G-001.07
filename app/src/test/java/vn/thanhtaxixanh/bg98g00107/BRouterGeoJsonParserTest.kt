package vn.thanhtaxixanh.bg98g00107

import vn.thanhtaxixanh.bg98g00107.navigation.BRouterGeoJsonParser
import vn.thanhtaxixanh.bg98g00107.navigation.ManeuverType
import org.junit.Assert.*
import org.junit.Test

class BRouterGeoJsonParserTest {
    private val sample = """
        {
          "type":"FeatureCollection",
          "features":[{
            "type":"Feature",
            "properties":{
              "track-length":"250",
              "total-time":"120",
              "times":[0,40,80,120],
              "voicehints":[[1,5,0,100,90],[2,13,2,80,40]]
            },
            "geometry":{
              "type":"LineString",
              "coordinates":[
                [106.0000,21.0000],
                [106.0005,21.0000],
                [106.0010,21.0000],
                [106.0015,21.0000]
              ]
            }
          }]
        }
    """.trimIndent()

    @Test fun parsesGeometryTimesAndBRouterVoiceHints() {
        val p = BRouterGeoJsonParser.parse(sample)
        assertEquals(4, p.points.size)
        assertEquals(4, p.timesSec.size)
        assertEquals(250, p.totalDistanceM)
        assertEquals(120, p.totalTimeSec)
        assertEquals(ManeuverType.RIGHT, p.maneuvers[0].type)
        assertEquals(ManeuverType.ROUNDABOUT, p.maneuvers[1].type)
        assertEquals(2, p.maneuvers[1].exitNumber)
    }

    @Test fun rejectsInvalidGeometry() {
        assertThrows(Exception::class.java) {
            BRouterGeoJsonParser.parse(
                """{"type":"FeatureCollection","features":[]}"""
            )
        }
    }
}
