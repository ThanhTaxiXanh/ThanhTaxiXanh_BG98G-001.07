package vn.thanhtaxixanh.bg98g00107

import org.junit.Assert.*
import org.junit.Test
import vn.thanhtaxixanh.bg98g00107.ai.YoloLabelOrder
import vn.thanhtaxixanh.bg98g00107.ai.VtsrLabelMapping
import org.json.JSONArray
import org.json.JSONObject

class VtsrIntegrationTest {
    @Test fun ultralyticsPythonNamesPreserveExplicitIndices() {
        assertEquals(listOf("P-102","R-303","W-224"),
            YoloLabelOrder.fromOnnxMetadata(
                "{0: 'P-102', 2: 'W-224', 1: 'R-303'}",3))
        assertNull(YoloLabelOrder.fromOnnxMetadata("{0: 'P-102', 2: 'W-224'}",3))
        assertNull(YoloLabelOrder.fromOnnxMetadata("{0: 'P-102', 0: 'W-224'}",2))
    }

    @Test fun vtsrIndexedMappingMustBeDense() {
        val map=JSONObject()
        val names=JSONObject()
        for (i in 0 until 56) names.put(i.toString(),
            JSONObject().put("code","P-"+(100+i)))
        map.put("id_to_label",names)
        val labels=VtsrLabelMapping.fromJson(map.toString(),56)
        assertEquals(56,labels?.size)
        assertEquals("P-100",labels?.get(0))
        assertEquals("P-155",labels?.get(55))
        names.remove("5")
        assertNull(VtsrLabelMapping.fromJson(map.toString(),56))
    }

    @Test fun descriptionsWithoutIndicesMustNeverBecomeClassOrder() {
        val descriptions=JSONObject().put("P-102","Cấm đi ngược chiều")
            .put("R-303","Nơi giao nhau chạy theo vòng xuyến")
        assertNull(VtsrLabelMapping.fromJson(descriptions.toString(),2))
        val arr=JSONArray().put("P-102").put("R-303")
        assertEquals(listOf("P-102","R-303"),
            VtsrLabelMapping.fromJson(
                JSONObject().put("labels",arr).toString(),2))
    }

    @Test fun modelLabelFileMustMatchClassCount() {
        assertNull(YoloLabelOrder.fromLabelsFile("P-102\nR-303",3))
        assertEquals(listOf("P-102","R-303"),
            YoloLabelOrder.fromLabelsFile("P-102\nR-303\n",2))
    }
}
