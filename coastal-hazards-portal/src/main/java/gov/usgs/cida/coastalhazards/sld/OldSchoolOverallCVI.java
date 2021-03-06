package gov.usgs.cida.coastalhazards.sld;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 *
 * @author Jordan Walker <jiwalker@usgs.gov>
 */
public final class OldSchoolOverallCVI {

    protected static final String[] attrs = {"CVIRISK"};
    protected static final float[] thresholds = {1.0f, 2.0f, 3.0f, 4.0f};
    protected static final String[] colors = {"#3B6800", "#FFFF00", "#FEAC00", "#FF0000"};
    protected static final String[] categories = {"Low", "Moderate", "High", "Very High"};
	
	protected static final String jspPath = "/SLD/categorical_line.jsp";
	protected static final String units = "";
	protected static final List<Map<String,Object>> bins;
	static {
		List<Map<String,Object>> binsResult = new ArrayList<Map<String,Object>>();
        for (int i=0; i<colors.length; i++) {
			Map<String, Object> binMap = new LinkedHashMap<String,Object>();
            binMap.put("category", categories[i]);
            binMap.put("color", colors[i]);
            binsResult.add(binMap);
        }
		
		bins = binsResult;
	}
	
	public static final SLDConfig overallOldSchool = new SLDConfig(
			jspPath, units, SLDGenerator.style, SLDGenerator.STROKE_WIDTH_DEFAULT, attrs, thresholds, colors, bins
	);

}
