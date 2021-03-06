package gov.usgs.cida.coastalhazards.wps;

import com.vividsolutions.jts.geom.LineString;
import com.vividsolutions.jts.geom.MultiLineString;
import com.vividsolutions.jts.geom.Point;
import com.vividsolutions.jts.index.strtree.STRtree;
import gov.usgs.cida.coastalhazards.util.BaselineDistanceAccumulator;
import gov.usgs.cida.coastalhazards.util.CRSUtils;
import static gov.usgs.cida.coastalhazards.util.Constants.*;
import gov.usgs.cida.coastalhazards.util.GeoserverUtils;
import gov.usgs.cida.coastalhazards.util.UTMFinder;
import gov.usgs.cida.coastalhazards.util.AttributeGetter;
import gov.usgs.cida.coastalhazards.wps.exceptions.LayerDoesNotExistException;
import gov.usgs.cida.coastalhazards.wps.exceptions.PoorlyDefinedBaselineException;
import gov.usgs.cida.coastalhazards.wps.exceptions.UnsupportedCoordinateReferenceSystemException;
import gov.usgs.cida.coastalhazards.wps.geom.Intersection;
import gov.usgs.cida.coastalhazards.wps.geom.ShorelineSTRTreeBuilder;
import gov.usgs.cida.coastalhazards.wps.geom.Transect;
import java.io.IOException;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import org.geoserver.catalog.Catalog;
import org.geoserver.catalog.DataStoreInfo;
import org.geoserver.catalog.LayerInfo;
import org.geoserver.wps.gs.GeoServerProcess;
import org.geoserver.wps.gs.ImportProcess;
import org.geotools.data.DataAccess;
import org.geotools.data.DataUtilities;
import org.geotools.data.DefaultTransaction;
import org.geotools.data.FeatureSource;
import org.geotools.data.Transaction;
import org.geotools.data.simple.SimpleFeatureCollection;
import org.geotools.data.simple.SimpleFeatureIterator;
import org.geotools.data.simple.SimpleFeatureStore;
import org.geotools.filter.FilterFactoryImpl;
import org.geotools.process.factory.DescribeParameter;
import org.geotools.process.factory.DescribeProcess;
import org.geotools.process.factory.DescribeResult;
import org.geotools.referencing.CRS;
import org.joda.time.DateTime;
import org.opengis.feature.Feature;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.feature.type.FeatureType;
import org.opengis.filter.FilterFactory;
import org.opengis.filter.PropertyIsEqualTo;
import org.opengis.referencing.crs.CoordinateReferenceSystem;

/**
 *
 * @author Jordan Walker <jiwalker@usgs.gov>
 */
@DescribeProcess(
        title = "Update Transects and Intersections",
        description = "Updates base_dist on transects and recalculate intersections for transect",
        version = "1.0.0")
public class UpdateTransectsAndIntersectionsProcess implements GeoServerProcess {
    
    private Catalog catalog;
    private GeoserverUtils gsUtils;
    private FilterFactory filterFactory;
    
    public UpdateTransectsAndIntersectionsProcess(Catalog catalog) {
        this.catalog = catalog;
        this.gsUtils = new GeoserverUtils(catalog);
        this.filterFactory = new FilterFactoryImpl();
    }
    
    @DescribeResult(name = "intersections", description = "intersection layer name")
    public String execute(
            @DescribeParameter(name = "transectLayer", description = "layer containing transects", min = 1, max = 1) String transectLayer,
            @DescribeParameter(name = "intersectionLayer", description = "layer containing intersections", min = 1, max = 1) String intersectionLayer,
            @DescribeParameter(name = "baselineLayer", description = "layer containing baseline", min = 1, max = 1) String baselineLayer,
            @DescribeParameter(name = "shorelines", description = "shoreline features", min = 1, max = 1) SimpleFeatureCollection shorelines,
            @DescribeParameter(name = "transectID", description = "edited transect id", min = 1, max = Integer.MAX_VALUE) int[] transectIds,
            @DescribeParameter(name = "farthest", description = "use farthest intersection on shoreline (default: false)", min = 0, max = 1) Boolean useFarthest) throws Exception {
        return new Process(transectLayer, intersectionLayer, baselineLayer, shorelines, transectIds, useFarthest).execute();
    }
    
    private class Process {
        
        private LayerInfo transectLayer;
        private LayerInfo intersectionLayer;
        private LayerInfo baselineLayer;
        private SimpleFeatureCollection shorelines;
        private int[] transectIds;
        private boolean useFarthest;

        private Process(String transectLayer, String intersectionLayer, String baselineLayer, SimpleFeatureCollection shorelines, int[] transectIds, Boolean useFarthest) {
            this.transectLayer = catalog.getLayerByName(transectLayer);
            this.intersectionLayer = catalog.getLayerByName(intersectionLayer);
            this.baselineLayer = catalog.getLayerByName(baselineLayer);
            this.shorelines = shorelines;
            this.transectIds = transectIds;
            this.useFarthest = (null == useFarthest) ? false : useFarthest;
        }
        
        private String execute() throws Exception {

            if (null == transectLayer || null == intersectionLayer || null == baselineLayer) {
                throw new LayerDoesNotExistException("Input layers must exist");
            }
            
            Transaction transaction = new DefaultTransaction("edit");
            
            DataStoreInfo transectDs = gsUtils.getDataStoreByName(
                                        transectLayer.getResource().getStore().getWorkspace().getName(),
                                        transectLayer.getResource().getStore().getName());
            DataAccess<? extends FeatureType, ? extends Feature> transectDa = gsUtils.getDataAccess(transectDs, null);
            SimpleFeatureStore transectStore = (SimpleFeatureStore)gsUtils.getFeatureSource(transectDa, transectLayer.getName());
            transectStore.setTransaction(transaction);
        
            DataStoreInfo intersectionDs = gsUtils.getDataStoreByName(
                                        intersectionLayer.getResource().getStore().getWorkspace().getName(),
                                        intersectionLayer.getResource().getStore().getName());
            DataAccess<? extends FeatureType, ? extends Feature> intersectionDa = gsUtils.getDataAccess(intersectionDs, null);
            SimpleFeatureStore intersectionStore = (SimpleFeatureStore)gsUtils.getFeatureSource(intersectionDa, intersectionLayer.getName());
            intersectionStore.setTransaction(transaction);
            AttributeGetter intersectionAttr = new AttributeGetter(intersectionStore.getSchema());
                    
            DataStoreInfo baselineDs = gsUtils.getDataStoreByName(
                                        baselineLayer.getResource().getStore().getWorkspace().getName(),
                                        baselineLayer.getResource().getStore().getName());
            DataAccess<? extends FeatureType, ? extends Feature> baselineDa = gsUtils.getDataAccess(baselineDs, null);
            FeatureSource<? extends FeatureType, ? extends Feature> baselineSource = gsUtils.getFeatureSource(baselineDa, baselineLayer.getName());

            CoordinateReferenceSystem shorelinesCrs = CRSUtils.getCRSFromFeatureCollection(shorelines);
            if (!CRS.equalsIgnoreMetadata(shorelinesCrs, REQUIRED_CRS_WGS84)) {
                throw new UnsupportedCoordinateReferenceSystemException("Shorelines are not in accepted projection");
            }
            CoordinateReferenceSystem utmCrs = UTMFinder.findUTMZoneCRSForCentroid((SimpleFeatureCollection)shorelines);
            if (utmCrs == null) {
                throw new IllegalStateException("Must have usable UTM zone to continue");
            }
            SimpleFeatureCollection transformedBaseline = CRSUtils.transformFeatureCollection((SimpleFeatureCollection)baselineSource.getFeatures(), baselineSource.getInfo().getCRS(), utmCrs);
            SimpleFeatureCollection transformedShorelines = CRSUtils.transformFeatureCollection(shorelines, REQUIRED_CRS_WGS84, utmCrs);
            
            STRtree strtree = new ShorelineSTRTreeBuilder(transformedShorelines).build();
            //DefaultFeatureCollection intersectionCollection = new DefaultFeatureCollection((SimpleFeatureCollection)intersectionStore.getFeatures());
            List<SimpleFeature> newIntersectionFeatures = new LinkedList<SimpleFeature>();
            for (int id : transectIds) {
                // use AttributeGetter to get real attr names
                PropertyIsEqualTo transectFilter = filterFactory.equals(filterFactory.property(TRANSECT_ID_ATTR), filterFactory.literal(id));
                PropertyIsEqualTo intersectionFilter = filterFactory.equals(filterFactory.property(TRANSECT_ID_ATTR), filterFactory.literal(id));
                try {
                    intersectionStore.removeFeatures(intersectionFilter);
                }
                catch (Exception e) {
                    transaction.rollback();
                    throw e;
                }
                                       
                SimpleFeatureCollection transectFeatures = (SimpleFeatureCollection)transectStore.getFeatures(transectFilter);
                SimpleFeature transect = null;
				
                SimpleFeatureIterator transectIterator = null;
				try {
					transectIterator = transectFeatures.features();
					while (transectIterator.hasNext()) {
						if (null == transect) {
							// I want the transformed transect, I'm really only using the iterator to get the ID
							transect = transectIterator.next();
						}
						else {
							throw new IllegalStateException("There shouldn't be more than one transect with the same id");
						}
					}
				} finally {
					if (null != transectIterator) {
						transectIterator.close();
					}
				}
                

                if (null != transect) {
                    Transect transectObj = Transect.fromFeature(transect);
                    
                    Map<DateTime, Intersection> newIntersections = Intersection.calculateIntersections(transectObj, strtree, useFarthest, intersectionAttr);
                    for (DateTime key : newIntersections.keySet()) {
                        Intersection newIntersection = newIntersections.get(key);
                        SimpleFeature newFeature = newIntersection.createFeature(intersectionStore.getSchema());
                        newIntersectionFeatures.add(newFeature);
                    }
                    Transect updatedTransect = updateTransectBaseDist(transectObj, transformedBaseline);
                    try {
                        transectStore.modifyFeatures(BASELINE_DIST_ATTR, updatedTransect.getBaselineDistance(), transectFilter);
                    }
                    catch (Exception e) {
                        transaction.rollback();
                        throw e;
                    }
                }
            }
            SimpleFeatureCollection collection = DataUtilities.collection(newIntersectionFeatures);
            try {
                intersectionStore.addFeatures(collection);
            }
            catch (Exception e) {
                transaction.rollback();
                throw e;
            }
            
            // rollback happens if any edit fails
            transaction.commit();
            transaction.close();
            return intersectionStore.getInfo().getName();
        }
        
        private Transect updateTransectBaseDist(Transect transect, SimpleFeatureCollection baseline) throws IOException {
			Transect result = null;
			
            BaselineDistanceAccumulator accumulator = new BaselineDistanceAccumulator();
            SimpleFeatureIterator iterator = null;
			try {
				iterator = baseline.features();
				while (null == result && iterator.hasNext()) {
					SimpleFeature feature = iterator.next();
					MultiLineString lines = CRSUtils.getLinesFromFeature(feature);
					for (int i=0; null == result && i<lines.getNumGeometries(); i++) {
						LineString line = (LineString)lines.getGeometryN(i);
						Point origin = transect.getOriginPoint();
						if (line.isWithinDistance(origin, BaselineDistanceAccumulator.EPS)) { // within a meter
							double accumulated = accumulator.accumulateToPoint(line, origin);
							transect.setBaselineDistance(accumulated);
							result = transect;
						} else {
							accumulator.accumulate(line);
						}
					}
				}
			} finally {
				if (null != iterator) {
					iterator.close();
				}
			}
			
            if (null == result) {
				throw new PoorlyDefinedBaselineException("Transect does not fall on baseline");
			}
			
			return result;
        }
    }
}
