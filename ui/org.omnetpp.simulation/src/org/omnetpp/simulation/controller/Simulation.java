package org.omnetpp.simulation.controller;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.lang.ref.WeakReference;
import java.net.SocketException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.commons.logging.impl.Jdk14Logger;
import org.apache.http.HttpException;
import org.apache.http.client.fluent.Request;
import org.eclipse.core.runtime.Assert;
import org.eclipse.swt.widgets.Display;
import org.omnetpp.common.Debug;
import org.omnetpp.common.engine.BigDecimal;
import org.omnetpp.common.json.JSONReader;
import org.omnetpp.common.util.StringUtils;
import org.omnetpp.simulation.model.cGate;
import org.omnetpp.simulation.model.cMessage;
import org.omnetpp.simulation.model.cModule;
import org.omnetpp.simulation.model.cObject;

/**
 * Interacts with a simulation process over HTTP.
 *
 * @author Andras
 */
//FIXME rename "failure mode" to "offline mode" everywhere
//TODO sendQuitCommand! also: implement CMD_QUIT in cmdenv.cc
public class Simulation {
    static boolean debugHttp = true;
    static boolean debugCache = true;

    // root object IDs
    public static final String ROOTOBJ_SIMULATION = "simulation";
    public static final String ROOTOBJ_FES = "fes";
    public static final String ROOTOBJ_SYSTEMMODULE = "systemModule";
    public static final String ROOTOBJ_DEFAULTLIST = "defaultList";
    public static final String ROOTOBJ_COMPONENTTYPES = "componentTypes";
    public static final String ROOTOBJ_NEDFUNCTIONS = "nedFunctions";
    public static final String ROOTOBJ_CLASSES = "classes";
    public static final String ROOTOBJ_ENUMS = "enums";
    public static final String ROOTOBJ_CLASSDESCRIPTORS = "classDescriptors";
    public static final String ROOTOBJ_CONFIGOPTIONS = "configOptions";
    public static final String ROOTOBJ_RESULTFILTERS = "resultFilters";
    public static final String ROOTOBJ_RESULTRECORDERS = "resultRecorders";

    public static final int CATEGORY_ALL = ~0;
    public static final int CATEGORY_MODULES = 0x01;
    public static final int CATEGORY_QUEUES = 0x02;
    public static final int CATEGORY_STATISTICS = 0x04;
    public static final int CATEGORY_MESSAGES = 0x08;
    public static final int CATEGORY_VARIABLES = 0x10;
    public static final int CATEGORY_MODPARAMS = 0x20;
    public static final int CATEGORY_CHANSGATES = 0x40;
    public static final int CATEGORY_OTHERS = 0x80;

    /**
     * The state in the simulation process as reported by it; see Cmdenv for state transitions
     */
    public enum SimState {  // FIXME still seems fishy around TERMINATED-ERROR-FINISHCALLED -- should be cleaned up. Rename TERMINATED to COMPLETED? define whether they include finish() having been called or not!!
        NONE, NONETWORK, READY, RUNNING, TERMINATED /*TODO COMPLETED -- ez egyebkent nincs sose!!*/, ERROR, FINISHCALLED  // as defined in cmdenv.h
        //TODO consider: BUSY (or ==INPROGRESS) and CANCELLED (useful if setupNetwork() and callFinish() can be cancelled)
    };

    public enum RunMode {
        NONE, STEP, NORMAL, FAST, EXPRESS
    }

    public enum StoppingReason {
        NONE, UNTILSIMTIME, UNTILEVENT, UNTILMODULE, UNTILMESSAGE,
        REALTIMECHUNK, STOPCOMMAND, TERMINATION;
        static boolean isUntil(StoppingReason r) {return r==UNTILSIMTIME || r==UNTILEVENT || r==UNTILMODULE || r==UNTILMESSAGE;}
    };

    /**
     * TODO
     */
    class StatusResponse {
    }

    // INP_GETS
    class GetsRequest extends StatusResponse {
        String prompt;
        String defaultValue;
    }

    // INP_ASKYESNO
    class AskYesNoRequest extends StatusResponse {
        String message;
    }

    // INP_MSGDIALOG
    class MsgDialogRequest extends StatusResponse {
        String message;
    }

    // INP_ASKPARAMETER
    class AskParameterRequest extends StatusResponse {
        String paramName;
        String ownerFullPath;
        String paramType;
        String prompt;
        String defaultValue;
        String unit;
        String[] choices;
    }

//    class ErrorInfo extends StatusResponse {
//        String message;
//    }

    private static final int MONGOOSE_MAX_REQUEST_URI_SIZE = 256*1024-1000; // see MAX_REQUEST_SIZE in mongoose.h

    private enum ContentToLoadEnum { OBJECT, FIELDS };

    private String urlBase;
    private int timeoutMillis = 30 * 1000;
    private Request currentHttpRequest;  // ongoing HTTP request, so that we can abort it from another thread when needed
    private boolean isOnline;
    private ISimulationCallback simulationCallback;

    // simulation status (as returned by the GET "/sim/status" request)
    private String hostName;
    private int portNumber;
    private long processId;
    private String[] argv;
    private String workingDir;
    private SimState simState;
    private StoppingReason stoppingReason = StoppingReason.NONE; // after run/runUntil
    private String configName;
    private int runNumber;
    private String networkName;
    private long lastEventNumber; // last event's event number
    private BigDecimal lastEventSimulationTime = BigDecimal.getZero(); // last event's simulation time
    //TODO last event module and message
    private long nextEventNumber;
    private BigDecimal nextEventSimulationTimeGuess = BigDecimal.getZero(); // hw-in-the-loop may inject new event before first one in the FES!
    private int nextEventModuleIdGuess;  //TODO why we use module ID? why not the module?
    private long nextEventMessageIdGuess; //TODO display on UI
    private long simulationChangeCounter; // cObject::changeCounter from the simulation process
    private String eventlogFile;

    // object cache
    private Map<String, cObject> rootObjects = new HashMap<String, cObject>(); // keys: "simulation", "network", etc.
    private Map<Long, WeakReference<cObject>> cachedObjects = new HashMap<Long, WeakReference<cObject>>();
    private long lastCacheRefreshSerial;  // simulationChangeCounter on last refreshObjectCache() call
    private int cacheRefreshSeq;

    // module logs
    private LogBuffer logBuffer = new LogBuffer();

    /**
     * Constructor.
     */
    public Simulation(String hostName, int portNumber) {
        this.hostName = hostName;
        this.portNumber = portNumber;
        this.urlBase = "http://" + hostName + ":" + portNumber + "/";
        this.simState = SimState.NONE;
        this.isOnline = false;

        // turn off log messages from Apache HttpClient if we can
        Log log = LogFactory.getLog("org.apache.commons.httpclient");
        if (log instanceof Jdk14Logger)
            ((Jdk14Logger) log).getLogger().setLevel(Level.OFF);
    }

    public void setSimulationCallback(ISimulationCallback simulationCallback) {
        this.simulationCallback = simulationCallback;
    }

    public ISimulationCallback getSimulationCallback() {
        return simulationCallback;
    }

    public int getTimeoutMillis() {
        return timeoutMillis;
    }

    public void setTimeoutMillis(int timeoutMillis) {
        this.timeoutMillis = timeoutMillis;
    }

    public boolean isOnline() {
        return isOnline;
    }

    public void setOnline(boolean isOnline) {
        this.isOnline = isOnline;
    }

    /**
     * Host where the simulation that we are talking to runs.
     */
    public String getHostName() {
        return hostName;
    }

    public int getPortNumber() {
        return portNumber;
    }

    public String getUrlBase() {
        return urlBase;
    }

    /**
     * Process ID of the simulation we are talking to.
     */
    public long getProcessId() {
        return processId;
    }

    /**
     * Command line of the simulation process we are talking to.
     */
    public String[] getArgv() {
        return argv;
    }

    /**
     * Working directory of the simulation process we are talking to.
     */
    public String getWorkingDir() {
        return workingDir;
    }

    public SimState getSimState() {
        return simState;
    }

    /**
     * Why the last Run command stopped.
     */
    public StoppingReason getStoppingReason() {
        return stoppingReason;
    }

    public String getConfigName() {
        return configName;
    }

    public int getRunNumber() {
        return runNumber;
    }

    public String getNetworkName() {
        return networkName;
    }

    public long getLastEventNumber() {
        return lastEventNumber;
    }

    public BigDecimal getLastEventSimulationTime() {
        return lastEventSimulationTime;
    }

    public long getNextEventNumber() {
        return nextEventNumber;
    }

    public BigDecimal getNextEventSimulationTimeGuess() {
        return nextEventSimulationTimeGuess;
    }

    public int getNextEventModuleIdGuess() {
        return nextEventModuleIdGuess;
    }

    public long getNextEventMessageIdGuess() {
        return nextEventMessageIdGuess;
    }

    public String getEventlogFile() {
        return eventlogFile;
    }

    public int getCacheRefreshSeq() {
        return cacheRefreshSeq;
    }

    public LogBuffer getLogBuffer() {
        return logBuffer;
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    public StatusResponse refreshStatus() throws CommunicationException {
        Object responseJSON = getPageContentAsJSON(urlBase + "sim/status");
        // store basic simulation state
        Map responseMap = (Map) responseJSON;
        long oldProcessId = processId;
        processId = ((Number) responseMap.get("processid")).longValue();
        if (oldProcessId != 0 && processId != oldProcessId) {
            //TODO dialog: not the same process as we used to talk to!
            throw new CommunicationException("Simulation process has been replaced! old process ID: " + oldProcessId + ", new process ID: " + processId);
        }
        hostName = (String) responseMap.get("hostname");
        argv = (String[]) ((List)responseMap.get("argv")).toArray(new String[]{});
        workingDir = (String) responseMap.get("wd");
        configName = (String) responseMap.get("config");
        runNumber = (int) defaultLongIfNull((Number) responseMap.get("run"), -1);
        networkName = (String) responseMap.get("network");
        simState = SimState.valueOf(((String) responseMap.get("state")).toUpperCase());
        stoppingReason = StoppingReason.valueOf(StringUtils.defaultString((String) responseMap.get("stoppingReason"), "none").toUpperCase());
        simulationChangeCounter = ((Number) responseMap.get("changeCounter")).longValue();
        eventlogFile = (String) responseMap.get("eventlogfile");

        lastEventNumber = defaultLongIfNull((Number) responseMap.get("lastEventNumber"), -1);
        lastEventSimulationTime = BigDecimal.parse(StringUtils.defaultIfEmpty((String) responseMap.get("lastEventSimtime"), "0"));
        nextEventNumber = defaultLongIfNull((Number) responseMap.get("nextEventNumber"), 0);
        nextEventSimulationTimeGuess = BigDecimal.parse(StringUtils.defaultIfEmpty((String) responseMap.get("nextEventSimtimeGuess"), "0"));
        nextEventModuleIdGuess = defaultIntegerIfNull((Number) responseMap.get("nextEventModuleIdGuess"), 0);
        nextEventMessageIdGuess = defaultLongIfNull((Number) responseMap.get("nextEventMessageIdGuess"), 0);

        // refresh root object IDs
        Map jsonRootObjects = (Map) responseMap.get("rootObjects");
        for (Object key : jsonRootObjects.keySet())
            rootObjects.put((String) key, getObjectByJSONRef((String) jsonRootObjects.get(key)));

        // parse action requested by the simulation
        StatusResponse request = null;
        if (responseMap.containsKey("userInput")) {
            Map jsonData = (Map) responseMap.get("userInput");
            String type = (String) jsonData.get("type");
            if (StringUtils.isBlank(type)) {
                throw new RuntimeException("missing userInput type");
            }
            else if (type.equals("askParameter")) {
                AskParameterRequest r = new AskParameterRequest();
                r.paramName = (String) jsonData.get("paramName");
                r.ownerFullPath = (String) jsonData.get("ownerFullPath");
                r.paramType = (String) jsonData.get("paramType");
                r.prompt = (String) jsonData.get("prompt");
                r.defaultValue = (String) jsonData.get("defaultValue");
                r.unit = (String) jsonData.get("unit");
                r.choices = null; //TODO
                request = r;
            }
            else if (type.equals("gets")) {
                GetsRequest r = new GetsRequest();
                r.prompt = (String) jsonData.get("prompt");
                r.defaultValue = (String) jsonData.get("defaultValue");
                request = r;
            }
            else if (type.equals("askYesNo")) {
                AskYesNoRequest r = new AskYesNoRequest();
                r.message = (String) jsonData.get("message");
                request = r;
            }
            else if (type.equals("msgDialog")) {
                MsgDialogRequest r = new MsgDialogRequest();
                r.message = (String) jsonData.get("message");
                request = r;
            }
            else {
                throw new RuntimeException("unrecognized userInput type: " + type);
            }
            if (debugHttp) Debug.println("  got request for user interaction: " + request.getClass().getSimpleName());
        }

        // load the log
        List logEntries = (List) responseMap.get("log");
        if (logEntries == null) logEntries = new ArrayList(0);
        EventEntry lastEventEntry = null;
        List<Object> logItems = new ArrayList<Object>();
        for (Object e : logEntries) {
            Map logEntry = (Map)e;
            String type = (String)logEntry.get("@");
            if (type.equals("E")) {
                if (!logItems.isEmpty()) {
                    if (lastEventEntry == null)
                        logBuffer.addEventEntry(lastEventEntry = new EventEntry());
                    lastEventEntry.logItems = logItems.toArray(new Object[]{});
                    logItems.clear();
                }

                lastEventEntry = new EventEntry();
                lastEventEntry.eventNumber = ((Number)logEntry.get("#")).longValue();
                lastEventEntry.simulationTime = BigDecimal.parse((String)logEntry.get("t"));
                lastEventEntry.moduleId = ((Number)logEntry.get("moduleId")).intValue();
                lastEventEntry.moduleFullPath = (String) logEntry.get("moduleFullPath");
                lastEventEntry.moduleNedType = (String) logEntry.get("moduleNedType");
                lastEventEntry.messageName = (String)logEntry.get("messageName");
                lastEventEntry.messageClassName = (String)logEntry.get("messageClassName");
                logBuffer.addEventEntry(lastEventEntry);
            }
            else if (type.equals("L")) {
                String chunk = (String)logEntry.get("txt");
                if (chunk.charAt(chunk.length()-1) == '\n')
                    chunk = chunk.substring(0, chunk.length()-1);  // remove trailing LF
                if (chunk.indexOf('\n') == -1) {
                    // add single line (typical case)
                    logItems.add(chunk);
                }
                else {
                    // split multi-line string to lines (rare case)
                    for (String line : chunk.split("\n", -1))
                        logItems.add(line);
                }
            }
            else if (type.equals("MB")) {
                Anim.ComponentMethodBeginEntry item = new Anim.ComponentMethodBeginEntry();
                item.srcModuleId = ((Number)logEntry.get("sm")).intValue();
                item.destModuleId = ((Number)logEntry.get("tm")).intValue();
                item.txt = (String) (String)logEntry.get("m");
                logItems.add(item);
            }
            else if (type.equals("ME")) {
                Anim.ComponentMethodEndEntry item = new Anim.ComponentMethodEndEntry();
                logItems.add(item);
            }
            else if (type.equals("BS")) {  //TODO no need for the simulation to send these entries (BS..ES) in Fast mode! (and of course not in Express mode)
                Anim.BeginSendEntry item = new Anim.BeginSendEntry();
                item.msg = (cMessage) getObjectByJSONRef((String)logEntry.get("msg"));
                logItems.add(item);
            }
            else if (type.equals("SH")) {
                Anim.MessageSendHopEntry item = new Anim.MessageSendHopEntry();
                item.srcGate = (cGate) getObjectByJSONRef((String)logEntry.get("srcGate"));
                item.propagationDelay = defaultBigDecimalIfNull((String)logEntry.get("propagationDelay"), null);
                item.transmissionDelay = defaultBigDecimalIfNull((String)logEntry.get("transmissionDelay"), null);
                logItems.add(item);
            }
            else if (type.equals("SD")) {
                Anim.MessageSendDirectEntry item = new Anim.MessageSendDirectEntry();
                item.srcModule = (cModule) getObjectByJSONRef((String)logEntry.get("srcModule"));
                item.destGate = (cGate) getObjectByJSONRef((String)logEntry.get("destGate"));
                item.propagationDelay = defaultBigDecimalIfNull((String)logEntry.get("propagationDelay"), null);
                item.transmissionDelay = defaultBigDecimalIfNull((String)logEntry.get("transmissionDelay"), null);
                logItems.add(item);
            }
            else if (type.equals("ES")) {
                Anim.EndSendEntry item = new Anim.EndSendEntry();
                logItems.add(item);
            }
            else {
                throw new RuntimeException("unknown log entry type '" + type + "'");
            }
        }
        if (!logItems.isEmpty()) {
            if (lastEventEntry == null)
                logBuffer.addEventEntry(lastEventEntry = new EventEntry());
            lastEventEntry.logItems = logItems.toArray(new Object[]{});
            logItems.clear();
        }

        // if we added something to the log, tell everyone interested about it
        if (lastEventEntry != null)
            logBuffer.fireChangeNotification();

        return request;
    }

    public void refreshObjectCache() throws CommunicationException {
        //
        // Maintain object cache:
        // - purge objects from cache that are unreferenced in Java, or have been deleted from C++;
        //   also unload (~clear) objects that have not been accessed lately, to reduce HTTP load
        //   and allow other objects they reference to be garbage collected
        // - refresh contents of already-filled objects
        // - refresh the detail fields of loaded objects too (where filled in)
        //
        if (debugCache)
            Debug.println("\n======= Refreshing object cache, seq=" + cacheRefreshSeq + " ========");
        List<Long> garbage = new ArrayList<Long>();
        List<cObject> objectsToReload = new ArrayList<cObject>();
        List<cObject> objectsToReloadFields = new ArrayList<cObject>();
        int numFilled = 0, numUnloads = 0;
        for (Long id : cachedObjects.keySet()) {
            cObject obj = cachedObjects.get(id).get();
            if (obj == null) {
                garbage.add(id);
            }
            else {
                Assert.isTrue(!obj.isDisposed(), "deleted objects should not be in the cache");
                if (obj.isFilledIn()) {
                    numFilled++;
                    if (obj.getLastAccessSeq() != getCacheRefreshSeq()) { // not accessed since last object cache refresh
                        if (debugCache)
                            Debug.println("unloading: " + obj.toString());
                        obj.unload();
                        numUnloads++;
                    }
                    objectsToReload.add(obj);
                }

                if (obj.isFieldsFilledIn()) {
                    objectsToReloadFields.add(obj);
                }
            }
        }
        cachedObjects.keySet().removeAll(garbage);
        doLoadObjects(objectsToReload, ContentToLoadEnum.OBJECT, true);
        doLoadObjects(objectsToReloadFields, ContentToLoadEnum.FIELDS, true);
        cacheRefreshSeq++;
        lastCacheRefreshSerial = simulationChangeCounter;

        if (debugCache)
            Debug.println("Object cache after refresh: size " + cachedObjects.size() + " (" + (numFilled-numUnloads) + " filled); " +
                    "refresh purged " + garbage.size() + ", unloaded " + numUnloads + ", reloaded " + objectsToReload.size() + ", fields-reloaded " + objectsToReloadFields.size());
    }

    /**
     * Send reply to a user interaction request.
     */
    public void sendReply(String value) throws CommunicationException {
        String params = (value == null) ? "" : "?value=" + urlEncode(value);
        getPageContent(urlBase + "sim/reply" + params);
    }

    public void sendCancelReply() throws CommunicationException {
        getPageContent(urlBase + "sim/reply");
    }

    public boolean hasRootObjects() {
        return !rootObjects.isEmpty();
    }

    /**
     * Use ROOTOBJ_xxx constants as keys.
     */
    public cObject getRootObject(String key) {
        Assert.isTrue(!rootObjects.isEmpty(), "refresh() needs to be called before getRootObjectId() can be invoked");
        return rootObjects.get(key);
    }

    public void loadObject(cObject object) throws CommunicationException {
        Set<cObject> objects = new HashSet<cObject>();
        objects.add(object);
        doLoadObjects(objects, ContentToLoadEnum.OBJECT, false);
    }

    public void loadObjectFields(cObject object) throws CommunicationException {
        Set<cObject> objects = new HashSet<cObject>();
        objects.add(object);
        doLoadObjects(objects, ContentToLoadEnum.FIELDS, false);
    }

    public void loadObjects(cObject[] objects) throws CommunicationException {
        doLoadObjects(Arrays.asList(objects), ContentToLoadEnum.OBJECT, false);
    }

    public void loadObjects(List<? extends cObject> objects) throws CommunicationException {
        doLoadObjects(objects, ContentToLoadEnum.OBJECT, false);
    }

    public void loadFieldsOfObjects(cObject[] objects) throws CommunicationException {
        doLoadObjects(Arrays.asList(objects), ContentToLoadEnum.FIELDS, false);
    }

    public void loadFieldsOfObjects(List<? extends cObject> objects) throws CommunicationException {
        doLoadObjects(objects, ContentToLoadEnum.FIELDS, false);
    }

    public void loadUnfilledObjects(cObject[] objects) throws CommunicationException {
        loadUnfilledObjects(Arrays.asList(objects));
    }

    public void loadUnfilledObjects(Collection<? extends cObject> objects) throws CommunicationException {
        // load objects that are not yet filled in
        List<cObject> missing = new ArrayList<cObject>();
        for (cObject obj : objects) {
            if (!obj.isFilledIn())
                missing.add(obj);
        }
        doLoadObjects(missing, ContentToLoadEnum.OBJECT, false);
    }

    public void loadFieldsOfUnfilledObjects(cObject[] objects) throws CommunicationException {
        loadFieldsOfUnfilledObjects(Arrays.asList(objects));
    }

    public void loadFieldsOfUnfilledObjects(Collection<? extends cObject> objects) throws CommunicationException {
        // load objects that are not yet filled in
        List<cObject> missing = new ArrayList<cObject>();
        for (cObject obj : objects) {
            if (!obj.isFieldsFilledIn())
                missing.add(obj);
        }
        doLoadObjects(missing, ContentToLoadEnum.FIELDS, false);
    }

    @SuppressWarnings("rawtypes")
    protected void doLoadObjects(Collection<? extends cObject> objects, ContentToLoadEnum what, boolean isRefresh) throws CommunicationException {
        if (objects.isEmpty())
            return;
        StringBuilder buf = new StringBuilder();
        for (cObject obj: objects)
            buf.append(obj.getObjectId()).append(',');
        String idsArg = buf.substring(0, buf.length()-1);  // trim trailing comma
        Object json = getPageContentAsJSON(urlBase + "/sim/getObjectInfo" +
                "?what=" + (what==ContentToLoadEnum.OBJECT ? "ic" : "d") +
                "&ids=" + idsArg +
                (isRefresh ? "&since=" + lastCacheRefreshSerial : "")
                );

        // process response; objects not in the response no longer exist, purge them from the cache
        List<Long> garbage = new ArrayList<Long>();
        for (cObject obj: objects) {
            Map jsonObjectInfo = json==null ? null : (Map) ((Map) json).get(String.valueOf(obj.getObjectId()));
            if (jsonObjectInfo == null) {
                garbage.add(obj.getObjectId()); // calling remove() here would cause ConcurrentModificationException
                obj.markAsDisposed();
            }
            else if (!jsonObjectInfo.isEmpty()) { // empty response means "no change since last refresh", i.e. nothing to do
                if (what==ContentToLoadEnum.OBJECT)
                    obj.fillFromJSON(jsonObjectInfo);
                else
                    obj.fillFieldsFromJSON(jsonObjectInfo);
            }
        }
        cachedObjects.keySet().removeAll(garbage);
    }

    public cObject getObjectByJSONRef(String idAndType) {
        if (idAndType.equals("0"))
            return null;
        int colonPos = idAndType.indexOf(':');
        if (colonPos == -1)
            throw new RuntimeException("argument should be in the form \"<id>:<classname>\": " + idAndType);
        long id = Long.valueOf(idAndType.substring(0, colonPos));
        WeakReference<cObject> ref = cachedObjects.get(id);
        cObject obj = ref == null ? null : ref.get();
        if (obj != null) {
            return obj;
        }
        else {
            String className = idAndType.substring(colonPos+1);
            obj = createBlankObject(id, className);
            cachedObjects.put(id, new WeakReference<cObject>(obj));
            return obj;
        }
    }

    protected cObject createBlankObject(long id, String knownBaseClass) {
        try {
            String name = "org.omnetpp.simulation.model." + knownBaseClass;
            Class<?> clazz = Class.forName(name);
            return (cObject)clazz.getConstructors()[0].newInstance(this, id);
        }
        catch (Exception e) {
            throw new RuntimeException("internal error: support for known C++ base class " + knownBaseClass, e);
        }
    }

    public List<cObject> searchForObjects(cObject root, String classNamePattern, String fullPathPattern, int categories, int maxCount) throws CommunicationException {
        List<cObject> result = new ArrayList<cObject>();

        String catStr = "";
        if ((categories & CATEGORY_ALL) != 0)
            catStr = "a";
        else {
            if ((categories & CATEGORY_MODULES) != 0) catStr += "m";
            if ((categories & CATEGORY_QUEUES) != 0) catStr += "q";
            if ((categories & CATEGORY_STATISTICS) != 0) catStr += "s";
            if ((categories & CATEGORY_MESSAGES) != 0) catStr += "g";
            if ((categories & CATEGORY_VARIABLES) != 0) catStr += "v";
            if ((categories & CATEGORY_MODPARAMS) != 0) catStr += "p";
            if ((categories & CATEGORY_CHANSGATES) != 0) catStr += "c";
            if ((categories & CATEGORY_OTHERS) != 0) catStr += "o";
        }

        Object json = getPageContentAsJSON(urlBase + "/sim/search?root=" + root.getObjectId() +
                (classNamePattern != null ? "&class=" + urlEncode(classNamePattern) : "") +
                (fullPathPattern != null ? "&fullpath=" + urlEncode(fullPathPattern) : "") +
                (categories != 0 ? "&cat=" + catStr : "") +
                (maxCount > 0 ? "&max=" + maxCount : "")
                );

        @SuppressWarnings("rawtypes")
        List objects = (List) ((Map)json).get("objects");
        for (Object objRef : objects)
            result.add(getObjectByJSONRef((String)objRef));

        return result;
    }

    @SuppressWarnings("rawtypes")
    public List<ConfigDescription> getConfigDescriptions() throws CommunicationException {
        Object json = getPageContentAsJSON(urlBase + "/sim/enumerateConfigs");
        List<ConfigDescription> result = new ArrayList<ConfigDescription>();

        for (Object jitem : (List) json) {
            Map jmap = (Map) jitem;
            ConfigDescription config = new ConfigDescription();
            config.name = (String) jmap.get("name");
            config.description = (String) jmap.get("description");
            config.numRuns = ((Number) jmap.get("numRuns")).intValue();
            List jextends = (List) jmap.get("extends");
            String[] tmp = new String[jextends.size()];
            for (int i = 0; i < tmp.length; i++)
                tmp[i] = (String) jextends.get(i);
            config.extendsList = tmp;
            result.add(config);
        }
        return result;
    }

    public void sendSetupRunCommand(String configName, int runNumber) throws CommunicationException {
        getPageContent(urlBase + "sim/setupRun?config=" + urlEncode(configName) + "&run=" + runNumber);
    }

    public void sendSetupNetworkCommand(String networkName) throws CommunicationException {
        getPageContent(urlBase + "sim/setupNetwork?network=" + urlEncode(networkName));
    }

    public void sendRebuildNetworkCommand() throws CommunicationException {
        getPageContent(urlBase + "sim/rebuild");
    }

    public void sendStepCommand() throws CommunicationException {
        getPageContent(urlBase + "sim/step");
    }

    public void sendRunCommand(RunMode mode, long realtimeMillis) throws CommunicationException {
        sendRunUntilCommand(mode, realtimeMillis, null, 0, null, null);
    }

    public void sendRunUntilCommand(RunMode mode, long realtimeMillis, BigDecimal untilSimTime, long untilEventNumber, cModule untilModule, cMessage untilMessage) throws CommunicationException {
        String untilArgs = "";
        if (realtimeMillis > 0)
            untilArgs += "&rtlimit=" + realtimeMillis;
        if (untilEventNumber > 0)
            untilArgs += "&uevent=" + untilEventNumber;
        if (untilSimTime != null)
            untilArgs += "&utime=" + untilSimTime.toString();
        if (untilModule != null)
            untilArgs += "&umod=" + untilModule.getObjectId();
        if (untilMessage != null)
            untilArgs += "&umsg=" + untilMessage.getObjectId();
        getPageContent(urlBase + "sim/run?mode=" + mode.name().toLowerCase() + untilArgs);
    }

    public void sendStopCommand() throws CommunicationException {
        getPageContent(urlBase + "sim/stop");
    }

    public void sendCallFinishCommand() throws CommunicationException {
        // strictly speaking, we shouldn't allow callFinish() after SIM_ERROR but it comes handy in practice...
        Assert.isTrue(simState == SimState.READY || simState == SimState.TERMINATED || simState == SimState.ERROR);
        getPageContent(urlBase + "sim/callFinish");
    }

    protected String urlEncode(String s) {
        try {
            return URLEncoder.encode(s, "US-ASCII");
        }
        catch (UnsupportedEncodingException e) {
            throw new RuntimeException(e);
        }
    }

    protected Object getPageContentAsJSON(String url) throws CommunicationException {
        //long startTime = System.currentTimeMillis();
        String response = getPageContent(url);
        if (response == null)
            return new HttpException("Received empty document in response to GET " + url);
        //if (debug) Debug.println("  - HTTP GET took " + (System.currentTimeMillis() - startTime) + "ms");

        // parse
        //startTime = System.currentTimeMillis();
        Object jsonTree = new JSONReader().read(response);
        //if (debug) Debug.println("  got: " + jsonTree.toString());
        //if (debug) Debug.println("  - JSON parsing took " + (System.currentTimeMillis() - startTime) + "ms");
        return jsonTree;
    }

    protected String getPageContent(String url) throws CommunicationException {
        if (debugHttp)
            Debug.println("GET " + url);
        if (!isOnline())
            throw new CommunicationException("Simulation Front-end is currently off-line");
        if (url.length() > MONGOOSE_MAX_REQUEST_URI_SIZE)
            throw new RuntimeException("Request URL length " + url.length() + "exceeds Mongoose limit " + MONGOOSE_MAX_REQUEST_URI_SIZE);

        try {
            long startTime = System.currentTimeMillis();
            String response = doHttpGet(url);
            if (debugHttp) Debug.println("  took " + (System.currentTimeMillis() - startTime) + "ms");
            if (response.length() > 1024)
                if (debugHttp) Debug.println("  response: ~" + ((response.length()+511)/1024) + "KiB");
            return response;
        }
        catch (SocketException e) {
            if (Thread.interrupted()) {
                simulationCallback.communicationInterrupted();
                throw new CommunicationException(e);
            }
            else {
                // likely connection refused -- very probably fatal
                simulationCallback.fatalCommunicationError(e);
                throw new CommunicationException(e);
            }
        }
        catch (IOException e) {
            // timeout or something like that -- likely transient failure
            simulationCallback.transientCommunicationFailure(e);
            throw new CommunicationException(e);
        }
    }

    public void ping() throws IOException {
        doHttpGet(urlBase + "sim/ping");
    }

    /**
     * A low-level HTTP GET method. Returns the response body.
     * @throws IOException
     */
    protected String doHttpGet(String url) throws IOException {
        try {
            currentHttpRequest = Request.Get(url);
            return currentHttpRequest.
                   connectTimeout(timeoutMillis).
                   socketTimeout(timeoutMillis).
                   execute().
                   returnContent().asString();
        }
        finally {
            currentHttpRequest = null;
        }
    }

    public void abortOngoingHttpRequest() {
        // for more info see e.g. http://devtcg.blogspot.hu/2008/07/interruptible-io-example-using.html
        if (currentHttpRequest != null) {
            Display.getDefault().getThread().interrupt(); // set interrupted flag
            currentHttpRequest.abort();  // does nothing if already aborted
            currentHttpRequest = null;
        }
    }

    private BigDecimal defaultBigDecimalIfNull(String txt, BigDecimal defaultValue) {
        return txt == null ? defaultValue : BigDecimal.parse(txt);
    }

    private int defaultIntegerIfNull(Number i, int defaultValue) {
        return i == null ? defaultValue : i.intValue();
    }

    private long defaultLongIfNull(Number l, long defaultValue) {
        return l == null ? defaultValue : l.longValue();
    }

}
