public class LayerGraphic extends AGraphic implements IGISLayer {

    private static final Logger LOGGER = LogManager.getLogger(LayerGraphic.class);

    private Set<IGISLayerListener> listeners = new HashSet<>();
    protected List<IGISGraphic> graphics = new ArrayList<>();
    private final Set<IGISGraphic> graphicsSet = new HashSet<>();  // O(1) lookup

    private IGISMouseListener mouseListener;
    private HashMap<RenderingProperty, Boolean> renderingPropertiesFor3D; //3d rendering properties for the layer
    private double refreshRate = 0;
    private FrameBuffer frameBuffer;

    public LayerGraphic(String name, IGISLayer parent) {
        super(name);
        if (parent != null) {
            parent.addChild(this);
            this.setParent(parent);
        }

        //set all properties false initially
        renderingPropertiesFor3D = new HashMap<>();
        for (RenderingProperty p : RenderingProperty.values()) {
            renderingPropertiesFor3D.put(p, false);
        }
    }

    @Override
    public void setRefreshRate(IGISMap2D map, double fps) {

        IGISFrameBufferController controller = map.getFrameBufferController();
        if (refreshRate > 0) {
            controller.removeLayer(this);
        }
        refreshRate = fps;
        controller.addLayer(this);
    }

    @Override
    public void setFrameBuffer(FrameBuffer frameBuffer) {
        this.frameBuffer = frameBuffer;
    }

    @Override
    public FrameBuffer getFrameBuffer() {
        return frameBuffer;
    }

    public void addLayerListener(IGISLayerListener listener) {
        listeners.add(listener);
    }

    public synchronized void setGraphicStyle(IGISGraphicStyle style) {
        for (IGISGraphic graphic : graphics) {
            String aString = style.getClass().getName();
            if (aString.contains("PolygonSymbolizer")) {
                String aGraphicString = graphic.getClass().getName();
                if (aGraphicString.contains("GraphicPolygon")) {
                    graphic.setGraphicStyle(style);
                }
            }
        }
    }

    public void layerChanged(IGISLayerChangeEvent event) {
        if (event != null) {
            for (IGISLayerListener listener : listeners) {
                try {
                    listener.layerChanged(event);
                } catch (Exception e) {
                    LOGGER.error(e.getMessage(), e);
                }
            }
        }
        invalidateLayer();
    }

    private void layerChanged(IGISGraphic[] graphics, int eventType) {

        if (listeners.size() == 0) {
            layerChanged(null);
        } else {
            layerChanged(new LayerChangeEvent(graphics, eventType));
        }
    }

    private void layerChanged(IGISGraphic[] graphics, DrawableGraphicInfo[] drawableGraphicInfos, int eventType) {

        if (listeners.size() == 0) {
            layerChanged(null);
        } else {
            layerChanged(new LayerChangeEvent(graphics, drawableGraphicInfos, eventType));
        }
    }

    void invalidateLayer() {
        if (getParent() != null) {
            ((LayerGraphic) getParent()).invalidateLayer();
        }
    }

    public void removeLayerListener(IGISLayerListener listener) {
        listeners.remove(listener);
    }

    public int getChildCount() {
        return graphics.size();
    }

    public IGISGraphic[] getChildren() {
        return graphics.toArray(new IGISGraphic[0]);
    }

    public IGISGraphic addChild(IGISGraphic child) {

        /**
         * If the LayerGraphics's visibility is false,
         * then set its child's visibility to false.
         */
        if (!visible) {
            child.setVisible(false);
        }

        return addChild(graphics.size(), child);
    }

    public IGISGraphic addChild(int index, IGISGraphic child) {
        if (!add(index, child)) {
            return null;
        }

        layerChanged(new IGISGraphic[]{child}, IGISLayerChangeEvent.ELEMENT_ADDED);
        return child;
    }

    public IGISGraphic[] addChildren(IGISGraphic[] children) {
        return addChildren(graphics.size(), children);
    }

    @Override
    public IGISGraphic[] addChildren(int aIndex, IGISGraphic[] children) {
        ArrayList<IGISGraphic> retValue = new ArrayList<>();
        int i = 0;
        for (IGISGraphic child : children) {
            if (add(aIndex + i, child)) {
                i++;
                retValue.add(child);
            }
        }

        IGISGraphic[] result = retValue.toArray(new IGISGraphic[0]);
        layerChanged(result, IGISLayerChangeEvent.ELEMENT_ADDED);

        return result;
    }

    private synchronized boolean add(int index, IGISGraphic child) {// TODO Birkan - Halil
        if (child == null || child.isScreenGraphic()) {
            return false;
        }

        int currentSize = graphics.size();
        if (index < 0 || index > currentSize) {
            return false;
        }

        if (graphicsSet.contains(child)) {
            return false;
        }

        graphics.add(index, child);
        graphicsSet.add(child);
        child.setParent(this);
        return true;
    }

    //Todo api üzerinden deprecate edildi, içeriye aç?k olmal? ve sadece AGraphic.deleteResources içinden çal??t?r?mal?
    public IGISGraphic removeChild(IGISGraphic child) {
        remove(child);

        layerChanged(new IGISGraphic[]{child}, IGISLayerChangeEvent.ELEMENT_REMOVED);

        return child;
    }

    //Todo komple silinmeli
    public synchronized void removeChildren() {
        if (graphics.isEmpty()) {
            return;
        }

        for (int i = graphics.size() - 1; i >= 0; i--) {
            remove(i);
        }

        layerChanged((IGISGraphic[]) null, IGISLayerChangeEvent.ELEMENT_REMOVED);
    }

    //Todo api üzerinden deprecate edildi, içeriye aç?k olmal? ve sadece AGraphic.deleteResources içinden çal??t?r?mal?
    public IGISGraphic[] removeChildren(IGISGraphic[] children) {
        ArrayList<IGISGraphic> collection = new ArrayList<>();
        for (IGISGraphic child : children) {
            if (remove(child)) {
                collection.add(child);
            }
        }

        IGISGraphic[] result = collection.toArray(new IGISGraphic[0]);
        layerChanged(result, IGISLayerChangeEvent.ELEMENT_REMOVED);

        return result;
    }

    private synchronized boolean remove(IGISGraphic child) {
        if (child == null) {
            return false;
        }

        if (!graphicsSet.contains(child)) {
            return false;
        }

        int index = graphics.indexOf(child);

        if (index >= 0) {
            remove(index);
            return true;
        }
        return false;
    }

    private synchronized void remove(int index) {
        if (index >= 0 && index < graphics.size()) {
            IGISGraphic graphic = graphics.get(index);

            graphic.setSelected(false);
            if (graphic.getParent() == this) {
                graphic.setParent(null);
            }

            graphics.remove(index);
            graphicsSet.remove(graphic);
        }
    }

    public synchronized IGISGraphic replaceChild(IGISGraphic oldChild, IGISGraphic newChild) {
        if (oldChild == newChild) {
            return null;
        }

        int oldIndex = -1;
        int newIndex = -1;

        for (int i = 0; i < graphics.size(); i++) {
            if (graphics.get(i) == oldChild) {
                oldIndex = i;
            }
            if (graphics.get(i) == newChild) {
                newIndex = i;
            }
        }

        if (oldIndex >= 0) {
            remove(oldIndex);
            add(oldIndex, newChild);
        } else if (newIndex >= 0) {
            remove(newIndex);
            add(newIndex, oldChild);
        }

        layerChanged(
            new IGISGraphic[]{oldChild, newChild},
            IGISLayerChangeEvent.ELEMENTS_REORDERED
        );
        return newChild;
    }

    public void setChildren(IGISGraphic[] children) {
        removeChildren();
        addChildren(children);
    }

    private boolean groupStatus;

    public void group() {
        groupStatus = true;
    }

    public boolean isGroup() {
        return groupStatus;
    }

    public void ungroup() {
        groupStatus = false;
    }

    public void setSelected(boolean selected) {
        if (selected == this.getSelected()) {
            return;
        }
        // TODO Gizem commented, needs reconsidering. Causes double
        // selection/deselection but may be cause some side effects
        // super.setSelected(aselected);
        setSelectedForLayer(selected);
        for (IGISGraphic graphic : graphics) {
            graphic.setSelected(selected);
        }
    }

    public void setSelectable(boolean dragSelectable) {
        super.setSelectable(dragSelectable);
        for (IGISGraphic graphic : graphics) {
            graphic.setSelectable(dragSelectable);
        }
    }

    public void setAutoEdit(boolean autoEdit) {
        super.setAutoEdit(autoEdit);
        for (IGISGraphic graphic : graphics) {
            graphic.setAutoEdit(autoEdit);
        }
    }

    public int indexOf(IGISGraphic child) {
        return graphics.indexOf(child);
    }

    public IGISGraphic[] getPrimitives() {
        ArrayList<IGISGraphic> primitives = new ArrayList<>();
        getPrimitives(primitives, this);
        return primitives.toArray(new IGISGraphic[0]);
    }

    private void getPrimitives(ArrayList<IGISGraphic> avisibleGraphicList, IGISGraphic graphic) {
        if (graphic instanceof IGISLayer) {
            IGISGraphic[] graphics1 = ((IGISLayer) graphic).getChildren();
            for (IGISGraphic igisGraphic : graphics1) {
                getPrimitives(avisibleGraphicList, igisGraphic);
            }
        } else {
            avisibleGraphicList.add(graphic);
        }
    }

    public IGISGraphic getChild(String childName) {
        for (IGISGraphic item : graphics) {
            if (item != null && item.getName() != null && childName != null
                && item.getName().equals(childName)) {
                return item;
            }
        }
        return null;
    }

    public void moveChild(int paramInt, IGISGraphic graphic) {
        int oldIndex = graphics.indexOf(graphic);
        if (graphics.size() <= 1 || oldIndex == -1 || oldIndex == paramInt
            || paramInt > graphics.size() || paramInt < 0) {
            return;
        }

        graphics.remove(graphic);
        if (paramInt >= graphics.size()) {
            graphics.add(graphic);
        } else {
            graphics.add(paramInt, graphic);
        }
        IGISGraphic[] aList = new IGISGraphic[1];
        aList[0] = graphic;
        LayerChangeEvent ace = new LayerChangeEvent(aList, IGISLayerChangeEvent.ELEMENTS_REORDERED);
        layerChanged(ace);
    }

    public GISEnvelope getBoundingBox(IGISMap2D map) {
        return boundingBox;
    }

    public GISEnvelope getUnionizedChildsBoundingBox(IGISMap2D map) {
        IGISGraphic[] primitives = getPrimitives();

        double latMin = 1000000;
        double lonMin = 1000000;
        double latMax = -1000000;
        double lonMax = -1000000;

        for (IGISGraphic primitive : primitives) {
            if (primitive != null && primitive.getVisible()) {
                GISEnvelope env = primitive.getBoundingBox(map);
                if (env != null) {
                    if (env.getLowerCorner().getX() < lonMin) {
                        lonMin = env.getLowerCorner().getX();
                    }
                    if (env.getLowerCorner().getY() < latMin) {
                        latMin = env.getLowerCorner().getY();
                    }
                    if (env.getUpperCorner().getX() > lonMax) {
                        lonMax = env.getUpperCorner().getX();
                    }
                    if (env.getUpperCorner().getY() > latMax) {
                        latMax = env.getUpperCorner().getY();
                    }
                }
            }
        }
        if (latMin != 1000000) {
            return new GISEnvelope(new GISPosition(lonMin, latMin), new GISPosition(lonMax, latMax));
        }
        return null;
    }

    public void gridChilds(int matrixSize) {
        GISEnvelope envelope = getUnionizedChildsBoundingBox(null);
        if (envelope == null) {
            return;
        }
        double minLon = envelope.getLowerCorner().getX();
        double minLat = envelope.getLowerCorner().getY();
        double maxLon = envelope.getUpperCorner().getX();
        double maxLat = envelope.getUpperCorner().getY();
        double latDif = (maxLat - minLat) * 1.00001;
        double lonDif = (maxLon - minLon) * 1.00001;
        double scale = Math.max(lonDif, latDif) / 360.0 / matrixSize;

        IGISGraphic[] children = getChildren();
        ArrayList<IGISGraphic> childrenVector = new ArrayList<>(Arrays.asList(children));
        ArrayList<GISPosition[]> overviewPointsPolygon = new ArrayList<>();
        ArrayList<GISPosition[]> overviewPointsPolyline = new ArrayList<>();
        IGISPolygonSymbolizer overviewPolygonSymbolizer = null;
        IGISLineSymbolizer overviewLineSymbolizer = null;
        HashMap<Integer, IGISLayer> groups = new HashMap<>();
        HashMap<Integer, ArrayList<IGISGraphic>> groupItems = new HashMap<>();
        for (IGISGraphic child : children) {
            GISEnvelope childEnvelope = (child).getBoundingBox(null);
            double lat = childEnvelope.getCenter(1);
            double lon = childEnvelope.getCenter(0);

            int latIndex = (int) ((lat - minLat) / latDif * matrixSize);
            int lonIndex = (int) ((lon - minLon) / lonDif * matrixSize);
            Integer key = latIndex * 1000 + lonIndex;
            ArrayList<IGISGraphic> group = groupItems.get(key);
            if (group == null) {
                IGISLayer groupagg = new LayerGraphic("Group " + key, null);
                groups.put(key, groupagg);
                group = new ArrayList<>();
                groupItems.put(key, new ArrayList<>());
                groupagg.setMaxScale(scale);
            }
            if (child instanceof IGISGraphicPolygon) {
                overviewPointsPolygon.add(((IGISGraphicPolygon) child).getExteriorRing());
                if (overviewPolygonSymbolizer == null) {
                    overviewPolygonSymbolizer = (IGISPolygonSymbolizer) ((IGISGraphicPolygon) child)
                        .getPolygonSymbolizer().clone();
                    overviewPolygonSymbolizer
                        .setFillOpacity(overviewPolygonSymbolizer.getFillOpacity() * 0.5f);
                    overviewPolygonSymbolizer
                        .setStrokeOpacity(overviewPolygonSymbolizer.getStrokeOpacity() * 0.5f);

                }
            } else if (child instanceof IGISGraphicLineString) {
                overviewPointsPolyline.add(((IGISGraphicLineString) child).getPoints());
                if (overviewLineSymbolizer == null) {
                    overviewLineSymbolizer = (IGISLineSymbolizer) ((IGISGraphicLineString) child)
                        .getLineSymbolizer().clone();
                    overviewLineSymbolizer.setStrokeOpacity(overviewLineSymbolizer.getStrokeOpacity() * 0.5f);
                }
            }
            childrenVector.remove(child);
            group.add(child);
        }
        for (Integer key : groupItems.keySet()) {
            ArrayList<IGISGraphic> childs = groupItems.get(key);
            if (childs.size() > 0) {
                IGISLayer agg = groups.get(key);
                agg.setChildren(childs.toArray(new IGISGraphic[0]));
                childrenVector.add(agg);
            }
        }
        setChildren(childrenVector.toArray(new IGISGraphic[0]));

        children = getChildren();
        for (IGISGraphic igisGraphic : children) {
            IGISGraphic[] childs = ((IGISLayer) igisGraphic).getChildren();
            ArrayList<IGISGraphicPolygon> polygons = new ArrayList<>();
            ArrayList<IGISGraphicLineString> lineStrings = new ArrayList<>();
            ArrayList<IGISGraphic> others = new ArrayList<>();
            ArrayList<IGISGraphic> combineds = new ArrayList<>();
            for (IGISGraphic child : childs) {
                if (child.getClass().getCanonicalName().contains("GraphicLineString")) {
                    lineStrings.add((IGISGraphicLineString) child);
                } else if (child.getClass().getCanonicalName().contains("GraphicPolygon")) {
                    polygons.add((IGISGraphicPolygon) child);
                } else {
                    others.add(child);
                }
            }
            if (lineStrings.size() > 0) {
                ArrayList<GISPosition> points = new ArrayList<>();
                for (IGISGraphicLineString lineString : lineStrings) {
                    GISPosition[] linestringpoints = lineString.getPoints();
                    Collections.addAll(points, linestringpoints);
                    points.add(null);
                }
                IGISGraphicLineString combined = lineStrings.get(0);
                combined.setPoints(points.toArray(new GISPosition[0]));
                combineds.add(combined);
            }
            if (polygons.size() > 0) {
                ArrayList<GISPosition> points = new ArrayList<>();
                ArrayList<IGISGraphicPolygon> bigPolygons = new ArrayList<>();
                for (IGISGraphicPolygon polygon : polygons) {
                    GISEnvelope bbox = polygon.getBoundingBox(null);
                    double xDif = bbox.getUpperCorner().getX() - bbox.getLowerCorner().getX();
                    double yDif = bbox.getUpperCorner().getY() - bbox.getLowerCorner().getY();
                    if (Math.min(xDif, yDif) < scale * 50) {
                        GISPosition[] linestringpoints = polygon.getExteriorRing();
                        points.addAll(Arrays.asList(linestringpoints));
                        points.add(null);
                    } else {
                        bigPolygons.add(polygon);
                    }

                }
                IGISGraphicPolygon combined = polygons.get(0);
                combined.setExteriorRing(points.toArray(new GISPosition[0]));
                combined.getPolygonSymbolizer()
                    .setTesselationWindingRule(IGISPolygonSymbolizer.TESS_WINDING_MULTI_POLYGON);
                combineds.add(combined);
                combineds.addAll(bigPolygons);
            }
            others.addAll(combineds);
            ((IGISLayer) igisGraphic).setChildren(others.toArray(new IGISGraphic[0]));
        }
        children = getChildren();
        for (IGISGraphic igisGraphic : children) {
            IGISLayer child = (IGISLayer) igisGraphic;
            child.setBoundingBox(child.getUnionizedChildsBoundingBox(null));
        }

        if (overviewPolygonSymbolizer != null) {
            ArrayList<GISPosition> overviewPoints2Polygon = new ArrayList<>();
            for (GISPosition[] temp : overviewPointsPolygon) {
                GISPosition[] bbox = GISGeodesy.getBoundingBox(temp);
                double xdif = bbox[1].getX() - bbox[0].getX();
                double ydif = bbox[1].getY() - bbox[0].getY();
                if (Math.max(Math.abs(xdif), Math.abs(ydif)) > scale * 3) {
                    overviewPoints2Polygon.addAll(Arrays.asList(temp));
                    if (overviewPoints2Polygon.size() > 0 && overviewPoints2Polygon.get(overviewPoints2Polygon.size() - 1) != null) {
                        overviewPoints2Polygon.add(null);
                    }
                }
            }
            IGISGraphicPolygon overviewPolygon = new GraphicPolygon("Overview Polygon");
            overviewPolygon.setExteriorRing(overviewPoints2Polygon
                .toArray(new GISPosition[0]));
            overviewPolygonSymbolizer
                .setTesselationWindingRule(IGISPolygonSymbolizer.TESS_WINDING_MULTI_POLYGON);
            overviewPolygon.setGraphicStyle(overviewPolygonSymbolizer);
            overviewPolygon.setSelectable(false);
            overviewPolygon.setAutoEdit(false);
            overviewPolygon.setMinScale(scale);
            addChild(overviewPolygon);
        }

        if (overviewLineSymbolizer != null) {
            ArrayList<GISPosition> overviewPoints2Polyline = new ArrayList<>();
            for (GISPosition[] temp : overviewPointsPolyline) {
                GISPosition[] bbox = GISGeodesy.getBoundingBox(temp);
                double xdif = bbox[1].getX() - bbox[0].getX();
                double ydif = bbox[1].getY() - bbox[0].getY();
                if (Math.max(Math.abs(xdif), Math.abs(ydif)) > scale * 3) {
                    overviewPoints2Polyline.addAll(Arrays.asList(temp));
                    if (overviewPoints2Polyline.size() > 0 && overviewPoints2Polyline.get(overviewPoints2Polyline.size() - 1) != null) {
                        overviewPoints2Polyline.add(null);
                    }
                }
            }

            IGISGraphicLineString overviewPolyline = new GraphicLineString("Overview Polyline");
            overviewPolyline.setPoints(overviewPoints2Polyline
                .toArray(new GISPosition[0]));
            overviewPolyline.setGraphicStyle(overviewLineSymbolizer);
            overviewPolyline.setSelectable(false);
            overviewPolyline.setAutoEdit(false);
            overviewPolyline.setMinScale(scale);
            addChild(overviewPolyline);
        }
    }

    boolean renderAfterGraphics = false;

    public void setRenderAfterChilds(boolean renderAfterGraphics) {
        this.renderAfterGraphics = renderAfterGraphics;
    }

    public boolean isRenderAfterChilds() {
        return renderAfterGraphics;
    }

    float transparency = 1.0f;

    public void setTransparency(float transparency) {
        this.transparency = transparency;
    }

    public float getTransparency() {
        return transparency;
    }

    public void setMouseListener(IGISMouseListener listener) {
        mouseListener = listener;
    }

    public IGISMouseListener getMouseListener() {
        return mouseListener;
    }

    @Override
    public ArrayList<String> getChildrenFeatureAttributeKeys() {
        IGISGraphic[] primitives = getPrimitives();
        ArrayList<String> keySet = new ArrayList<>();
        for (IGISGraphic primitive : primitives) {
            ArrayList<String> childKeys = primitive.getFeatureAttributeKeys();
            for (String iter : childKeys) {
                if (!keySet.contains(iter)) {
                    keySet.add(iter);
                }
            }
        }
        for (int i = 0; i < primitives.length; i++) {
            for (String iter : keySet) {
                if (!primitives[i].getFeatureAttributeKeys().contains(iter)) {
                    keySet.remove(iter);
                    i = i - 1;
                    break;
                }
            }
        }
        return keySet;
    }

    @Override
    public double[] getFeatureMinMax(String key) {
        IGISGraphic[] primitives = getPrimitives();
        double minValue = Double.MAX_VALUE;
        double maxValue = -Double.MAX_VALUE;
        for (IGISGraphic primitive : primitives) {
            Object value = primitive.getFeatureAttribute(key);
            if (value != null) {
                try {
                    double doubleValue = Double.parseDouble("" + value);
                    if (doubleValue < minValue) {
                        minValue = doubleValue;
                    }
                    if (doubleValue > maxValue) {
                        maxValue = doubleValue;
                    }
                } catch (Exception e) {
                    LOGGER.warn(e.getMessage(), e);
                    return null;
                }
            }
        }
        if (minValue == Double.MAX_VALUE || maxValue == -Double.MAX_VALUE || minValue == maxValue) {
            return null;
        }
        return new double[]{minValue, maxValue};
    }

    @Override
    public ArrayList<IGISGraphic> getPrimitives(GISEnvelope boundary, String textFilter, String[] filterKeys,
                                                Double[] minValues, Double[] maxValues) {
        IGISGraphic[] primitives = getPrimitives();
        if (textFilter != null) {
            textFilter = textFilter.toLowerCase();
        }
        ArrayList<IGISGraphic> retValue = new ArrayList<>();
        for (IGISGraphic primitive : primitives) {
            GISEnvelope bbox = primitive.getBoundingBox(null);
            if (boundary == null || bbox == null || GISGeodesy.isEnvelopeContains(boundary, bbox)) {
                boolean valid = false;
                if (textFilter != null && textFilter.length() > 0) {
                    if (primitive.getName().contains(textFilter)) {
                        valid = true;
                    } else {
                        ArrayList<String> childKeys = primitive.getFeatureAttributeKeys();
                        for (String iter : childKeys) {
                            Object featureObject = primitive.getFeatureAttribute(iter);
                            if (featureObject != null) {
                                String featureObjectString = "" + featureObject;
                                featureObjectString = featureObjectString.toLowerCase();
                                if (featureObjectString.contains(textFilter)) {
                                    valid = true;
                                    break;
                                }
                            }
                        }
                    }
                } else {
                    valid = true;
                }
                if (valid) {
                    for (String iter : filterKeys) {
                        Object featureObject = primitive.getFeatureAttribute(iter);
                        if (featureObject != null) {
                            try {
                                double doubleValue = Double.parseDouble("" + featureObject);
                                for (int j = 0; j < filterKeys.length; j++) {
                                    if (doubleValue < minValues[j] || doubleValue > maxValues[j]) {
                                        valid = false;
                                        break;
                                    }
                                }
                            } catch (Exception e) {
                                LOGGER.warn(e.getMessage(), e);
                                valid = false;
                                break;
                            }
                        }
                    }
                }
                if (valid) {
                    retValue.add(primitive);
                }
            }
        }
        return retValue;
    }

    @Override
    public void setBlinkPeriod(int blinkPeriod) {
        super.setBlinkPeriod(blinkPeriod);
        IGISGraphic[] primitives = getPrimitives();
        for (IGISGraphic primitive : primitives) {
            primitive.setBlinkPeriod(blinkPeriod);
        }
    }

    @Override
    public void createOverview() {
        GISEnvelope envelope = getUnionizedChildsBoundingBox(null);
        if (envelope == null) {
            return;
        }

        IGISGraphic[] children = getChildren();
        ArrayList<GISPosition[]> overviewPointsPolygon = new ArrayList<>();
        ArrayList<GISPosition[]> overviewPointsPolyline = new ArrayList<>();
        IGISPolygonSymbolizer overviewPolygonSymbolizer = null;
        IGISLineSymbolizer overviewLineSymbolizer = null;
        for (IGISGraphic child : children) {
            if (child instanceof IGISGraphicPolygon) {
                overviewPointsPolygon.add(((IGISGraphicPolygon) child).getExteriorRing());
                if (overviewPolygonSymbolizer == null) {
                    overviewPolygonSymbolizer = (IGISPolygonSymbolizer) ((IGISGraphicPolygon) child)
                        .getPolygonSymbolizer().clone();
                    overviewPolygonSymbolizer
                        .setFillOpacity(overviewPolygonSymbolizer.getFillOpacity() * 0.5f);
                    overviewPolygonSymbolizer
                        .setStrokeOpacity(overviewPolygonSymbolizer.getStrokeOpacity() * 0.5f);

                }
            } else if (child instanceof IGISGraphicLineString) {
                overviewPointsPolyline.add(((IGISGraphicLineString) child).getPoints());
                if (overviewLineSymbolizer == null) {
                    overviewLineSymbolizer = (IGISLineSymbolizer) ((IGISGraphicLineString) child)
                        .getLineSymbolizer().clone();
                    overviewLineSymbolizer.setStrokeOpacity(overviewLineSymbolizer.getStrokeOpacity() * 0.5f);
                }
            }
        }
        double minLon = envelope.getLowerCorner().getX();
        double minLat = envelope.getLowerCorner().getY();
        double maxLon = envelope.getUpperCorner().getX();
        double maxLat = envelope.getUpperCorner().getY();
        double latDif = (maxLat - minLat) * 1.00001;
        double lonDif = (maxLon - minLon) * 1.00001;
        double scale = Math.max(lonDif, latDif) / 360.0 / 4;

        if (overviewPolygonSymbolizer != null) {
            ArrayList<GISPosition> overviewPoints2Polygon = new ArrayList<>();
            for (GISPosition[] temp : overviewPointsPolygon) {
                GISPosition[] bbox = GISGeodesy.getBoundingBox(temp);
                double xdif = bbox[1].getX() - bbox[0].getX();
                double ydif = bbox[1].getY() - bbox[0].getY();
                if (Math.max(Math.abs(xdif), Math.abs(ydif)) > scale * 3) {
                    overviewPoints2Polygon.addAll(Arrays.asList(temp));
                    if (overviewPoints2Polygon.size() > 0 && overviewPoints2Polygon.get(overviewPoints2Polygon.size() - 1) != null) {
                        overviewPoints2Polygon.add(null);
                    }
                }
            }
            IGISGraphicPolygon overviewPolygon = new GraphicPolygon("Overview Polygon");
            overviewPolygon.setExteriorRing(overviewPoints2Polygon
                .toArray(new GISPosition[0]));
            overviewPolygonSymbolizer
                .setTesselationWindingRule(IGISPolygonSymbolizer.TESS_WINDING_MULTI_POLYGON);
            overviewPolygon.setGraphicStyle(overviewPolygonSymbolizer);
            overviewPolygon.setSelectable(false);
            overviewPolygon.setAutoEdit(false);
            overviewPolygon.setMinScale(scale);
            addChild(overviewPolygon);
        }

        if (overviewLineSymbolizer != null) {
            ArrayList<GISPosition> overviewPoints2Polyline = new ArrayList<>();
            for (GISPosition[] temp : overviewPointsPolyline) {
                GISPosition[] bbox = GISGeodesy.getBoundingBox(temp);
                double xdif = bbox[1].getX() - bbox[0].getX();
                double ydif = bbox[1].getY() - bbox[0].getY();
                if (Math.max(Math.abs(xdif), Math.abs(ydif)) > scale * 3) {
                    overviewPoints2Polyline.addAll(Arrays.asList(temp));
                    if (overviewPoints2Polyline.size() > 0 && overviewPoints2Polyline.get(overviewPoints2Polyline.size() - 1) != null) {
                        overviewPoints2Polyline.add(null);
                    }
                }
            }

            IGISGraphicLineString overviewPolyline = new GraphicLineString("Overview Polyline");
            overviewPolyline.setPoints(overviewPoints2Polyline
                .toArray(new GISPosition[0]));
            overviewPolyline.setGraphicStyle(overviewLineSymbolizer);
            overviewPolyline.setSelectable(false);
            overviewPolyline.setAutoEdit(false);
            overviewPolyline.setMinScale(scale);
            addChild(overviewPolyline);
        }
    }

    @Override
    public int getFeautreAttributeType(String key) {
        IGISGraphic[] primitives = getPrimitives();
        int booleanCount = 0;
        int integerCount = 0;
        int doubleCount = 0;
        int stringCount = 0;
        for (IGISGraphic primitive : primitives) {
            Object value = primitive.getFeatureAttribute(key);
            if (value == null) {
                return FEATURE_ATTRIBUTE_TYPE_UNKNOWN;
            } else if (value instanceof Boolean) {
                booleanCount++;
            } else if (value instanceof Integer) {
                integerCount++;
            } else if (value instanceof Double) {
                doubleCount++;
            } else if (("" + value).equals("true") || ("" + value).equals("false")) {
                booleanCount++;
            } else {
                String strValue = "" + value;
                try {
                    Integer.parseInt(strValue);
                    integerCount++;
                } catch (Exception e) {
                    LOGGER.warn(e.getMessage(), e);
                    try {
                        Double.parseDouble(strValue);
                        doubleCount++;
                    } catch (Exception exception) {
                        LOGGER.warn(exception.getMessage(), exception);
                        stringCount++;
                    }
                }
            }
        }
        if (stringCount > 0) {
            return FEATURE_ATTRIBUTE_TYPE_STRING;
        }
        if (booleanCount > 0 && (integerCount == 0 && doubleCount == 0)) {
            return FEATURE_ATTRIBUTE_TYPE_BOOLEAN;
        }
        if (integerCount > 0 && (booleanCount == 0 && doubleCount == 0)) {
            return FEATURE_ATTRIBUTE_TYPE_INTEGER;
        }
        if (doubleCount > 0 && (booleanCount == 0 && integerCount == 0)) {
            return FEATURE_ATTRIBUTE_TYPE_DOUBLE;
        }
        return 0;
    }

    @Override
    public ArrayList<IGISGraphic> filterPrimitives(Map<String, List<Object>> filters) {
        IGISGraphic[] primitives = getPrimitives();
        if (filters == null) {
            return null;
        }
        ArrayList<IGISGraphic> retValue = new ArrayList<>();
        for (IGISGraphic primitive : primitives) {
            boolean valid = false;
            ArrayList<String> childKeys = primitive.getFeatureAttributeKeys();
            for (String iter : childKeys) {
                if (filters.containsKey(iter) && filters.get(iter).size() != 0) {
                    Object featureObject = primitive.getFeatureAttribute(iter);
                    if (featureObject != null) {
                        valid = filters.get(iter).contains(featureObject);
                    }
                }
            }
            if (valid) {
                primitive.setVisible(false);
            } else {
                primitive.setVisible(true);
            }
        }
        return retValue;
    }

    @Override
    public void setVisible(boolean visible) {
        IGISGraphic[] graphics = getChildren();

        for (IGISGraphic graphic : graphics) {
            graphic.setVisible(visible);
        }

        super.setVisible(visible);
    }

    @Override
    public void set3DRenderingProperty(RenderingProperty property, boolean value) {
        renderingPropertiesFor3D.put(property, value);
    }

    @Override
    public boolean get3DRenderingProperty(RenderingProperty property) {

        try {
            return renderingPropertiesFor3D.get(property);
        } catch (Exception e) {
            LOGGER.warn(e.getMessage(), e);
            return false;
        }
    }

    @Override
    public double getRefreshRate() {
        return refreshRate;
    }

    HashMap<Integer, Boolean> blinkStatus = new HashMap<>();
    HashMap<Integer, Long> blinkTimes = new HashMap<>();

    Map<Font, ArrayList<IGISGraphicLabel>> fontLabelMap = new HashMap<>();

    //TODO* graphics parametresini devreden ç?kar, layer kendi grafiklerini zaten biliyor

    /**
     * Renders visible graphic list
     *
     * @param selectionMode selection mode for rendering used internally
     * @return number of rendered graphics
     */
    public int renderVisibleGraphicList(Map2D map, AGISGL gl, boolean selectionMode) {

        RendererManager rendererManager = map.getRendererManager();
        GLRendererGraphicLabel2 labelRenderer = (GLRendererGraphicLabel2) rendererManager.getRenderer(IGISGraphicLabel.GRAPHIC_TYPE);

        ArrayList<IGISGraphic> graphics = map.getVisibilityController().findVisibleGraphics(this, 1.0);

        int renderedGraphicNumber = 0;

        gl.glPushMatrix();
        if (map.refMapState().getRotation() != 0) {
            gl.glRotated(map.refMapState().getRotation(), 0, 0, 1);
        }
        map.makeLatLonTranslation();

        long currentTime = System.currentTimeMillis();

        //todo blink test et
        for (Integer key : blinkTimes.keySet()) {
            long blinkTime = blinkTimes.get(key);
            Boolean blinkNegative = blinkStatus.get(key);
            if (currentTime - blinkTime > key) {
                blinkTimes.put(key, currentTime);
                blinkNegative = !blinkNegative;
                blinkStatus.put(key, blinkNegative);
            }
        }

        gl.glTranslated(0, 0, 0);
        boolean app6TextModifiersFocusedRendered = false;
        // show 3 labels at most on focus
        for (IGISGraphic graphic : graphics) {
            if (graphic != null) {

                if (graphic instanceof IGISLayer) {
                    continue;
                }

                gl.glPushMatrix();
                boolean blinking = graphic.getBlinkingWithParents(currentTime);
                boolean render = true;
                if (blinking) {
                    Boolean blinkStatusOfGraphic = blinkStatus.get(graphic.getBlinkPeriod());
                    if (blinkStatusOfGraphic == null) {
                        blinkStatusOfGraphic = true;
                        blinkStatus.put(graphic.getBlinkPeriod(), blinkStatusOfGraphic);
                        blinkTimes.put(graphic.getBlinkPeriod(), currentTime);
                    }
                    if (blinkStatusOfGraphic) {
                        render = false;
                    }
                }

                if (render) {
                    if (graphic.getVisible() && graphic.isVisible2D()) {
                        renderedGraphicNumber++;

                        IGISGraphicRenderer renderer = rendererManager.getRenderer(graphic);
                        renderer.render(gl, graphic, selectionMode);

                        //todo post-render metoduna ta??nabilir
                        if (graphic instanceof IGISGraphicIcon) {
                            //render icon texts
                            IGISGraphicIcon icon = (IGISGraphicIcon) graphic;

                            double scale = 1.0;
                            if ((renderer.getFocusedDistance() != -1 && renderer.getFocusedDistance() < 10) && !app6TextModifiersFocusedRendered) {
                                scale = 1.5;
                                app6TextModifiersFocusedRendered = true;
                            }
                            if (renderer instanceof IGISGraphicRendererApp6 && (!((IGISGraphicRendererApp6) renderer).isApp6PointRepresentationEnabled() || scale == 1.5)) {
                                if ((renderer.getFocusedDistance() != -1 || renderer.getFocusedDistance() < 10)) {
                                    updateFontMap(map, icon.getApp6Labels(map, scale), icon);
                                }
                            }

                            updateFontMap(map, icon.getCustomLabels(), icon);
                        }

                        gl.glTranslated(0, 0, 0);
                        if (!graphic.isAllLabelsAreNull() && graphic.isShowingLabel()) {
                            renderLabels(map, gl, graphic, labelRenderer);
                        }

                        graphic.firePostDisplay();
                        //todo focused graphics
//                                if (renderer.getFocusedDistance() != -1 && renderer.getFocusedDistance() < 10) {
//                                    focusedGraphics.add(graphic);
//                                }
                    }
                }
                gl.glPopMatrix();
                gl.glTranslated(0, 0, map.getZOrderInterval() * 4);
            }
        }

        renderIconLabelsAsGroup(fontLabelMap, gl, labelRenderer);
        gl.glPopMatrix();

        return renderedGraphicNumber;
    }

    private void updateFontMap(IGISMap2D map, IGISGraphicLabel[] labels, IGISGraphicIcon icon) {
        for (IGISGraphicLabel label : labels) {
            label.setPosition(icon.getPosition());
            Font font = label.getTextSymbolizer().getFont();
            if (((GLRendererGraphicIcon) (map.getRendererManager().getRenderer(IGISGraphicIcon.GRAPHIC_TYPE))).isAutoScaleEnabled()) {
                font = font.deriveFont((float) (font.getSize() / (map.getScale() * 14)));
            }
            if (!fontLabelMap.containsKey(font)) {
                ArrayList<IGISGraphicLabel> iconList = new ArrayList<>();
                iconList.add(label);
                fontLabelMap.put(font, iconList);
            } else {
                fontLabelMap.get(font).add(label);
            }
        }
    }

    private void renderIconLabelsAsGroup(Map<Font, ArrayList<IGISGraphicLabel>> fontMap, AGISGL gl, GLRendererGraphicLabel2 labelRenderer) {
        labelRenderer.renderAll(fontMap, gl, false);
        fontMap.clear();
    }

    private void renderLabels(IGISMap2D map, AGISGL gl, IGISGraphic graphic, GLRendererGraphicLabel2 labelRenderer) {

        for (int labelIndex = 0; labelIndex < 6; labelIndex++) {
            if (graphic.getLabel(labelIndex) != null && graphic.getLabel(labelIndex).length() > 0) {
                int labelOrientation = graphic.getLabelBox();
                IGISGraphicLabel label = map.createGraphicLabel();
                label.setGraphicStyle(map.getLabelTextSymbolizer());
                label.setText(graphic.getLabel(labelIndex));
                label.setParent(graphic.getParent());
                GISEnvelope bbox = graphic.getBoundingBox(map);
                boolean renderLabel = true;
                label.setPosition(new GISPosition(bbox.getLowerCorner().getX() * 0.5 + bbox.getUpperCorner().getX() * 0.5, bbox.getLowerCorner().getY() * 0.5 + bbox.getUpperCorner().getY() * 0.5));
                if (labelIndex == IGISGraphic.LABEL_ORIENTATION_BOTTOM_RIGHT) {
                    label.getTextSymbolizer().setXAnchor(IGISTextSymbolizer.XANCHOR_LEFT);
                    label.getTextSymbolizer().setXDisplacement(5);
                    label.getTextSymbolizer().setYDisplacement(-5);
                    label.setPosition(new GISPosition(bbox.getUpperCorner().getX(), bbox.getLowerCorner().getY()));
                } else if (labelIndex == IGISGraphic.LABEL_ORIENTATION_BOTTOM_LEFT) {
                    label.getTextSymbolizer().setXAnchor(IGISTextSymbolizer.XANCHOR_RIGHT);
                    label.setPosition(new GISPosition(bbox.getLowerCorner().getX(), bbox.getLowerCorner().getY()));
                    label.getTextSymbolizer().setXDisplacement(-5);
                    label.getTextSymbolizer().setYDisplacement(-5);
                } else if (labelIndex == IGISGraphic.LABEL_ORIENTATION_TOP_RIGHT) {
                    label.getTextSymbolizer().setXAnchor(IGISTextSymbolizer.XANCHOR_LEFT);
                    label.setPosition(new GISPosition(bbox.getUpperCorner().getX(), bbox.getUpperCorner().getY()));
                    label.getTextSymbolizer().setXDisplacement(5);
                    label.getTextSymbolizer().setYDisplacement(5);
                } else if (labelIndex == IGISGraphic.LABEL_ORIENTATION_TOP_LEFT) {
                    label.getTextSymbolizer().setXAnchor(IGISTextSymbolizer.XANCHOR_RIGHT);
                    label.setPosition(new GISPosition(bbox.getLowerCorner().getX(), bbox.getUpperCorner().getY()));
                    label.getTextSymbolizer().setXDisplacement(-5);
                    label.getTextSymbolizer().setYDisplacement(5);
                } else if (labelIndex == IGISGraphic.LABEL_ORIENTATION_ROTATED && graphic instanceof IGISGraphicCurvable) {
                    GISPosition pos1 = null;
                    GISPosition pos2 = null;
                    double angle;

                    GISPosition[] points = ((IGISGraphicCurvable) graphic).getPointsForRendering();
                    renderLabel = false;
                    if (points != null && points.length > 2) {
                        GISPosition nearestPoint = GISGeodesy.getNearestPointOn2DLine(points, label.getPosition(), GISGeodesy.ACCURACY_DISTANCE);
                        for (int j = 0; j < points.length - 1; j++) {
                            if (points[j] != null && points[j + 1] != null) {
                                double legDistance = GISGeodesy.getDistanceLinear(points[j], points[j + 1]);
                                double distance1 = GISGeodesy.getDistanceLinear(points[j], nearestPoint);
                                double distance2 = GISGeodesy.getDistanceLinear(points[j + 1], nearestPoint);
                                if ((distance1 + distance2) / legDistance > 0.99 && (distance1 + distance2) / legDistance < 1.01) {
                                    pos1 = points[j];
                                    pos2 = points[j + 1];
                                }
                            }
                        }
                        if (pos1 != null && pos2 != null) {
                            angle = (90 - GISGeodesy.getBearing(pos1, pos2, ((IGISGraphicCurvable) graphic).getPathType()) + 720) % 360;
                            if (angle > 90 && angle < 270) {
                                angle = angle + 180;
                            }
                            label.setPosition(nearestPoint);
                            label.getTextSymbolizer().setAllowingRotation(true);
                            label.getTextSymbolizer().setRotation(angle);
                            renderLabel = true;
                        }
                    }
                }
                if (labelOrientation == IGISGraphic.LABEL_ORIENTED_TO_SHAPE && (labelIndex != IGISGraphic.LABEL_ORIENTATION_ROTATED && labelIndex != IGISGraphic.LABEL_ORIENTATION_CENTER && graphic instanceof IGISGraphicCurvable)) {
                    GISPosition nearestPoint = GISGeodesy.getNearestPointOn2DLine(((IGISGraphicCurvable) graphic).getPointsForRendering(), label.getPosition(), GISGeodesy.GREAT_CIRCLE);
                    label.setPosition(nearestPoint);
                }
                if (renderLabel) {
                    labelRenderer.render(gl, label, false);
                }
            }
        }
    }

    @Override
    public List<IGISGraphic> getGraphics() {

        ArrayList<IGISGraphic> result = new ArrayList<>();
        for (IGISGraphic graphic : graphics) {
            if (graphic instanceof IGISLayer) {
                result.addAll(((IGISLayer) graphic).getGraphics());
            } else {
                result.add(graphic);
            }
        }
        return result;
    }

    @Override
    public void getGraphics(List<IGISGraphic> result) {
        result.addAll(graphics);
    }


    public void deleteResources(AGISGL gl) {
        if (getParent() != null) {
            getParent().removeChild(this);
        }

        if (graphics.size() == 0) {
            return;
        }

        for (int i = graphics.size() - 1; i >= 0; i--) {
            IGISGraphic graphic = graphics.get(i);
            remove(i);
            ((AGraphic) graphic).deleteResources(gl);
        }

        layerChanged((IGISGraphic[]) null, IGISLayerChangeEvent.ELEMENT_REMOVED);
    }
}
