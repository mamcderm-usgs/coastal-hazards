package gov.usgs.cida.coastalhazards.export;

import com.vividsolutions.jts.geom.Geometry;
import gov.usgs.cida.gml.GMLStreamingFeatureCollection;
import java.io.File;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import org.apache.commons.io.FileUtils;
import org.geotools.data.DataUtilities;
import org.geotools.data.DefaultTransaction;
import org.geotools.data.FileDataStoreFactorySpi;
import org.geotools.data.FileDataStoreFinder;
import org.geotools.data.Query;
import org.geotools.data.Transaction;
import org.geotools.data.shapefile.ShapefileDataStore;
import org.geotools.data.simple.SimpleFeatureCollection;
import org.geotools.data.simple.SimpleFeatureIterator;
import org.geotools.data.simple.SimpleFeatureSource;
import org.geotools.data.simple.SimpleFeatureStore;
import org.geotools.data.wfs.WFSDataStore;
import org.geotools.data.wfs.WFSDataStoreFactory;
import org.geotools.factory.CommonFactoryFinder;
import org.geotools.factory.GeoTools;
import org.geotools.feature.simple.SimpleFeatureBuilder;
import org.geotools.referencing.crs.DefaultGeographicCRS;
import org.junit.Ignore;
import org.junit.Test;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.feature.simple.SimpleFeatureType;
import org.opengis.feature.type.AttributeDescriptor;
import org.opengis.filter.FilterFactory2;
import org.opengis.filter.PropertyIsEqualTo;
import org.opengis.filter.spatial.Contains;

/**
 *
 * @author jiwalker
 */
public class FeatureCollectionExportTest {

    /**
     * Test of writeToShapefile method, of class FeatureCollectionExport.
     * ignoring this so it doesn't hit the server too much, mock this out for real test
     */
    @Test
    @Ignore
    public void testWriteToShapefile() throws Exception {
        WFSDataStoreFactory datastore = new WFSDataStoreFactory();
        Map params = new HashMap<>();
        params.put(WFSDataStoreFactory.URL.key, new URL("http://coastalmap.marine.usgs.gov/cmgp/National/cvi_WFS/MapServer/WFSServer?service=WFS&request=GetCapabilities&version=1.0.0"));
        params.put(WFSDataStoreFactory.WFS_STRATEGY.key, "arcgis");
        params.put(WFSDataStoreFactory.TIMEOUT.key, 15000);
        params.put(WFSDataStoreFactory.TRY_GZIP.key, "true");
        WFSDataStore wfs = datastore.createDataStore(params);
        String[] typeNames = wfs.getTypeNames();
        SimpleFeatureSource featureSource = wfs.getFeatureSource(typeNames[2]);
        
        FeatureCollectionExport featureCollectionExport = new FeatureCollectionExport(featureSource.getFeatures(), new File("/tmp/shpfile"), "test3");
        featureCollectionExport.addAttribute("CVIRISK");
        featureCollectionExport.writeToShapefile();
    }
    
    @Test
    @Ignore
    public void testWriteSolo() throws Exception {
        WFSDataStoreFactory datastore = new WFSDataStoreFactory();
        Map params = new HashMap<>();
        params.put(WFSDataStoreFactory.URL.key, new URL("http://coastalmap.marine.usgs.gov/cmgp/National/cvi_WFS/MapServer/WFSServer?service=WFS&request=GetCapabilities&version=1.0.0"));
        params.put(WFSDataStoreFactory.WFS_STRATEGY.key, "arcgis");
        params.put(WFSDataStoreFactory.TIMEOUT.key, 15000);
        params.put(WFSDataStoreFactory.TRY_GZIP.key, "true");
        WFSDataStore wfs = datastore.createDataStore(params);
        String[] typeNames = wfs.getTypeNames();
        SimpleFeatureSource featureSource = wfs.getFeatureSource(typeNames[2]);
        SimpleFeatureType schema = featureSource.getSchema();
        FileDataStoreFactorySpi factory = FileDataStoreFinder.getDataStoreFactory("shp");
        Map datastoreConfig = new HashMap<>();
        datastoreConfig.put("url", FileUtils.getFile(FileUtils.getTempDirectory(), "test3.shp").toURI().toURL());
        ShapefileDataStore shpfileDataStore = (ShapefileDataStore)factory.createNewDataStore(datastoreConfig);
        shpfileDataStore.createSchema(schema);
        shpfileDataStore.forceSchemaCRS(DefaultGeographicCRS.WGS84);

        SimpleFeatureStore featureStore = (SimpleFeatureStore) shpfileDataStore.getFeatureSource();
        Transaction t = new DefaultTransaction();

        // Copied directly from Import process
        featureStore.setTransaction(t);
        Query query = new Query();
        query.setCoordinateSystem(DefaultGeographicCRS.WGS84);
        SimpleFeatureIterator fi = featureSource.getFeatures(query).features();
        SimpleFeatureBuilder fb = new SimpleFeatureBuilder(schema);
        while (fi.hasNext()) {
            SimpleFeature source = fi.next();
            fb.reset();
            for (AttributeDescriptor desc : schema.getAttributeDescriptors()) {
                fb.set(desc.getName(), source.getAttribute(desc.getName()));
            }
            SimpleFeature target = fb.buildFeature(null);
            target.setDefaultGeometry(source.getDefaultGeometry());
            featureStore.addFeatures(DataUtilities.collection(target));
        }
        t.commit();
        t.close();
    }
    
    @Test
    @Ignore
    public void testHttpComponentsGetter() throws Exception {
        HttpComponentsWFSClient wfs = new HttpComponentsWFSClient();
        wfs.setupDatastoreFromEndpoint("http://cida-wiwsc-cchdev:8081/geoserver/wfs");
        //FilterFactory filterFactory = CommonFactoryFinder.getFilterFactory(GeoTools.getDefaultHints());
        SimpleFeatureCollection featureCollection = wfs.getFeatureCollection("proxied:atl_cvi", null);
        SimpleFeatureType schema = GMLStreamingFeatureCollection.unwrapSchema(featureCollection.getSchema());
        FileDataStoreFactorySpi factory = FileDataStoreFinder.getDataStoreFactory("shp");
        Map datastoreConfig = new HashMap<>();
        datastoreConfig.put("url", FileUtils.getFile(FileUtils.getTempDirectory(), "test3.shp").toURI().toURL());
        ShapefileDataStore shpfileDataStore = (ShapefileDataStore)factory.createNewDataStore(datastoreConfig);
        shpfileDataStore.createSchema(schema);
        shpfileDataStore.forceSchemaCRS(DefaultGeographicCRS.WGS84);

        SimpleFeatureStore featureStore = (SimpleFeatureStore) shpfileDataStore.getFeatureSource();
        Transaction t = new DefaultTransaction();

        // Copied directly from Import process
        featureStore.setTransaction(t);
        Query query = new Query();
        query.setCoordinateSystem(DefaultGeographicCRS.WGS84);
        SimpleFeatureIterator fi = featureCollection.features();
        SimpleFeatureBuilder fb = new SimpleFeatureBuilder(schema);
        while (fi.hasNext()) {
            SimpleFeature source = fi.next();
            fb.reset();
            for (AttributeDescriptor desc : schema.getAttributeDescriptors()) {
                fb.set(desc.getName(), source.getAttribute(desc.getName()));
            }
            SimpleFeature target = fb.buildFeature(null);
            target.setDefaultGeometry(source.getDefaultGeometry());
            featureStore.addFeatures(DataUtilities.collection(target));
        }
        t.commit();
        t.close();
    }
    
    @Test
    @Ignore // for now
    public void splitterTest() throws Exception {
        //get geometry
        HttpComponentsWFSClient wfs1 = new HttpComponentsWFSClient();
        wfs1.setupDatastoreFromEndpoint("http://cida-wiwsc-cchdev:8081/geoserver/wfs");
        FilterFactory2 filterFactory = CommonFactoryFinder.getFilterFactory2(GeoTools.getDefaultHints());
        PropertyIsEqualTo equals = filterFactory.equals(filterFactory.property("STATEFP"), filterFactory.literal(37));
        SimpleFeatureCollection featureCollection = wfs1.getFeatureCollection("splitter:tl_2013_coastal_states", equals);
        SimpleFeatureIterator features = featureCollection.features();
        //only deal with one
        Geometry geom = null;
        if (features.hasNext()) {
            SimpleFeature next = features.next();
            geom = (Geometry)next.getDefaultGeometry();
            
        }

        // then use geometry as filter
        HttpComponentsWFSClient wfs2 = new HttpComponentsWFSClient();
        wfs2.setupDatastoreFromEndpoint("http://cida-wiwsc-cchdev:8081/geoserver/wfs");
        FilterFactory2 filterFactory2 = CommonFactoryFinder.getFilterFactory2(GeoTools.getDefaultHints());
        Contains contains = filterFactory2.contains(filterFactory2.property("the_geom"), filterFactory2.literal(geom));
        
        SimpleFeatureCollection featureCollection2 = wfs2.getFeatureCollection("proxied:atl_cvi", contains);
        SimpleFeatureType schema = GMLStreamingFeatureCollection.unwrapSchema(featureCollection2.getSchema());
        FileDataStoreFactorySpi factory = FileDataStoreFinder.getDataStoreFactory("shp");
        Map datastoreConfig = new HashMap<>();
        datastoreConfig.put("url", FileUtils.getFile(FileUtils.getTempDirectory(), "splitter.shp").toURI().toURL());
        ShapefileDataStore shpfileDataStore = (ShapefileDataStore)factory.createNewDataStore(datastoreConfig);
        shpfileDataStore.createSchema(schema);
        shpfileDataStore.forceSchemaCRS(DefaultGeographicCRS.WGS84);

        SimpleFeatureStore featureStore = (SimpleFeatureStore) shpfileDataStore.getFeatureSource();
        Transaction t = new DefaultTransaction();

        // Copied directly from Import process
        featureStore.setTransaction(t);
        Query query = new Query();
        query.setCoordinateSystem(DefaultGeographicCRS.WGS84);
        SimpleFeatureIterator fi = featureCollection2.features();
        SimpleFeatureBuilder fb = new SimpleFeatureBuilder(schema);
        while (fi.hasNext()) {
            SimpleFeature source = fi.next();
            fb.reset();
            for (AttributeDescriptor desc : schema.getAttributeDescriptors()) {
                fb.set(desc.getName(), source.getAttribute(desc.getName()));
            }
            SimpleFeature target = fb.buildFeature(null);
            target.setDefaultGeometry(source.getDefaultGeometry());
            featureStore.addFeatures(DataUtilities.collection(target));
        }
        t.commit();
        t.close();
    }
}
