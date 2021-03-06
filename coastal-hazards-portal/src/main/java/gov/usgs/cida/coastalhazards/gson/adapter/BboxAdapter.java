package gov.usgs.cida.coastalhazards.gson.adapter;

import com.google.gson.JsonArray;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;
import com.vividsolutions.jts.geom.Envelope;
import gov.usgs.cida.coastalhazards.model.Bbox;
import java.lang.reflect.Type;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 *
 * @author Jordan Walker <jiwalker@usgs.gov>
 */
public class BboxAdapter implements JsonSerializer<Bbox>, JsonDeserializer<Bbox>{

    @Override
    public JsonElement serialize(Bbox src, Type typeOfSrc, JsonSerializationContext context) {
        JsonArray bboxArray = new JsonArray();

        Envelope envelope = parseBOX(src.getBbox());
        JsonPrimitive minx = new JsonPrimitive(envelope.getMinX());
        JsonPrimitive miny = new JsonPrimitive(envelope.getMinY());
        JsonPrimitive maxx = new JsonPrimitive(envelope.getMaxX());
        JsonPrimitive maxy = new JsonPrimitive(envelope.getMaxY());
        bboxArray.add(minx);
        bboxArray.add(miny);
        bboxArray.add(maxx);
        bboxArray.add(maxy);

        return bboxArray;
    }
    
    public static Envelope parseBOX(String box) {
        Envelope envelope = null;
        Pattern pattern = Pattern.compile("BOX\\(\\s*([-\\d\\.]+)\\s+([-\\d\\.]+)\\s*,\\s*([-\\d\\.]+)\\s+([-\\d\\.]+)\\s*\\)");
        Matcher matcher = pattern.matcher(box);
        if (matcher.matches()) {
            double minX = Double.parseDouble(matcher.group(1));
            double minY = Double.parseDouble(matcher.group(2));
            double maxX = Double.parseDouble(matcher.group(3));
            double maxY = Double.parseDouble(matcher.group(4));
            envelope = new Envelope(minX, maxX, minY, maxY);
        }
        return envelope;
    }

    @Override
    public Bbox deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
        Bbox bbox = new Bbox();
        if (json instanceof JsonArray) {
            JsonArray array = (JsonArray)json;
            if (array.size() != 4) {
                throw new JsonParseException("Bbox must be of format [minX,minY,maxX,maxY]");
            }
            double minX = array.get(0).getAsDouble();
            double minY = array.get(1).getAsDouble();
            double maxX = array.get(2).getAsDouble();
            double maxY = array.get(3).getAsDouble();
            bbox.setBbox(minX, minY, maxX, maxY);
        } else {
            throw new JsonParseException("Bbox must be JSON array");
        }
        return bbox;
    }

}
