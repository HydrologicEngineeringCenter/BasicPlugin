/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package basicplugin;
import com.rma.io.DssFileManagerImpl;
import com.rma.io.RmaFile;
import hec.heclib.dss.DSSPathname;
import hec.heclib.dss.HecDSSDataAttributes;
import hec.io.DSSIdentifier;
import hec.io.TimeSeriesContainer;
import hec2.model.DataLocation;
import hec2.plugin.PathnameUtilities;
import hec2.plugin.model.ComputeOptions;
import hec2.plugin.model.ModelAlternative;
import hec2.plugin.selfcontained.SelfContainedPluginAlt;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.jdom.Document;
import org.jdom.Element;
/**
 *
 * @author WatPowerUser
 */
public class BasicAlternative extends SelfContainedPluginAlt{
    private List<DataLocation> _dataLocations = new ArrayList<>();
    private String _pluginVersion;
    private static final String DocumentRoot = "BasicAlternative";
    private static final String AlternativeNameAttribute = "Name";
    private static final String AlternativeDescriptionAttribute = "Desc";
    private ComputeOptions _computeOptions;
    private double _maxVal;
    public BasicAlternative(){
        super();
        _dataLocations = new ArrayList<>();
    }
    public BasicAlternative(String name){
        this();
        setName(name);
    }
    @Override
    public boolean saveData(RmaFile file){
        if(file!=null){
            Element root = new Element(DocumentRoot);
            root.setAttribute(AlternativeNameAttribute,getName());
            root.setAttribute(AlternativeDescriptionAttribute,getDescription());
            if(_dataLocations!=null){
                saveDataLocations(root,_dataLocations);
            }
            Document doc = new Document(root);
            return writeXMLFile(doc,file);
        }
        return false;
    }
    @Override
    protected boolean loadDocument(org.jdom.Document dcmnt) {
        if(dcmnt!=null){
            org.jdom.Element ele = dcmnt.getRootElement();
            if(ele==null){
                System.out.println("No root element on the provided XML document.");
                return false;   
            }
            if(ele.getName().equals(DocumentRoot)){
                setName(ele.getAttributeValue(AlternativeNameAttribute));
                setDescription(ele.getAttributeValue(AlternativeDescriptionAttribute));
            }else{
                System.out.println("XML document root was imporoperly named.");
                return false;
            }
            if(_dataLocations==null){
                _dataLocations = new ArrayList<>();
            }
            _dataLocations.clear();
            loadDataLocations(ele, _dataLocations);
            setModified(false);
            return true;
        }else{
            System.out.println("XML document was null.");
            return false;
        }
    }
    public List<DataLocation> getOutputDataLocations(){
       //construct output data locations 
	return defaultDataLocations();
    }
    public List<DataLocation> getInputDataLocations(){
        //construct input data locations.
	return defaultDataLocations();
    }
    private List<DataLocation> defaultDataLocations(){
       	if(!_dataLocations.isEmpty()){
            //locations have previously been set (most likely from reading
            //in an existing alternative file.
            for(DataLocation dl : _dataLocations){
                String dlparts = dl.getDssPath();
                DSSPathname p = new DSSPathname(dlparts);
                if(p.aPart()==""&&p.bPart()==""&&p.cPart()==""&&p.dPart()==""&&p.ePart()==""&&p.fPart()==""){
                    if(validLinkedToDssPath(dl)){
                        setDssParts(dl);
                    }
                }
            }
            return _dataLocations;
        }
        List<DataLocation> dlList = new ArrayList<>();
        //create a default location so that links can be initialized.
        DataLocation dloc = new DataLocation(this.getModelAlt(),_name,"Any");
        dlList.add(dloc);
	return dlList; 
    }
    public boolean setDataLocations(List<DataLocation> dataLocations){
        boolean retval = false;
        for(DataLocation dl : dataLocations){
            if(!_dataLocations.contains(dl)){
                DataLocation linkedTo = dl.getLinkedToLocation();
                String dssPath = linkedTo.getDssPath();
                if(validLinkedToDssPath(dl))
                {
                    setModified(true);
                    setDssParts(dl);
                    _dataLocations.add(dl);
                    retval = true;
                }
            }else{
                DataLocation linkedTo = dl.getLinkedToLocation();
                String dssPath = linkedTo.getDssPath();
                if(validLinkedToDssPath(dl))
                {
                    setModified(true);
                    setDssParts(dl);;
                    retval = true;
                }
            }
        }
        if(retval)saveData();
	return retval;
    }
    private boolean validLinkedToDssPath(DataLocation dl){
        DataLocation linkedTo = dl.getLinkedToLocation();
        String dssPath = linkedTo.getDssPath();
        return !(dssPath == null || dssPath.isEmpty());
    }
    private void setDssParts(DataLocation dl){
        DataLocation linkedTo = dl.getLinkedToLocation();
        String dssPath = linkedTo.getDssPath();
        DSSPathname p = new DSSPathname(dssPath);
        String[] parts = p.getParts();
        parts[1] = parts[1] + " Output";
        ModelAlternative malt = this.getModelAlt();
        malt.setProgram(BasicPlugin.PluginName);
        parts[5] = "C000000:" + _name + ":" + PathnameUtilities.getWatFPartModelPart(malt);
        p.setParts(parts);
        dl.setDssPath(p.getPathname());
    }
    public void setComputeOptions(ComputeOptions opts){
        _computeOptions = opts;
    }
    @Override
    public boolean isComputable() {
        return true;
    }
    @Override
    public boolean compute() {
        if(_computeOptions instanceof hec2.wat.model.ComputeOptions){
            boolean returnValue = true;
            hec2.wat.model.ComputeOptions wco = (hec2.wat.model.ComputeOptions)_computeOptions;
            double multiplier = 1.0;
            if(wco.isFrmCompute()){
                //stochastic
                multiplier = wco.getEventRandom();
            }else{
                //deterministic
                multiplier = 2.0;
            }
            String dssFilePath = wco.getDssFilename();
            for(DataLocation dl : _dataLocations){
            //read input data source
                String dssPath = dl.getLinkedToLocation().getDssPath();
                TimeSeriesContainer tsc = ReadTimeSeries(dssFilePath,dssPath,wco.isFrmCompute());
            //multiply input data
                TimeSeriesContainer output = UpdateTimeSeries(tsc,multiplier);
            //write output data    
                if(!WriteTimeSeries(output,dl,dssFilePath)){
                    returnValue = false;
                }
            }
            return returnValue;
        }
        return false;
    }
    private TimeSeriesContainer ReadTimeSeries(String DssFilePath, String dssPath, boolean isFRM){
        DSSPathname pathName = new DSSPathname(dssPath);
        String InputFPart = pathName.getFPart();
        if(isFRM){
            int AltFLastIdx = _computeOptions.getFpart().lastIndexOf(":");
            if(InputFPart.contains(":")){
                int oldFLastIdx = InputFPart.lastIndexOf(":");
                pathName.setFPart(_computeOptions.getFpart().substring(0,AltFLastIdx)+ InputFPart.substring(oldFLastIdx,InputFPart.length()));
            }  
        }
        DSSIdentifier eventDss = new DSSIdentifier(DssFilePath,pathName.getPathname());
        eventDss.setStartTime(_computeOptions.getRunTimeWindow().getStartTime());
	eventDss.setEndTime(_computeOptions.getRunTimeWindow().getEndTime());
        int type = DssFileManagerImpl.getDssFileManager().getRecordType(eventDss);
        if((HecDSSDataAttributes.REGULAR_TIME_SERIES<=type && type < HecDSSDataAttributes.PAIRED)){
            boolean exist = DssFileManagerImpl.getDssFileManager().exists(eventDss);
            TimeSeriesContainer eventTsc = null;
            if (!exist )
            {
                try
                {
                    Thread.sleep(1000);
                }
                catch (InterruptedException e)
                {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                }
            }
            eventTsc = DssFileManagerImpl.getDssFileManager().readTS(eventDss, true);
            if ( eventTsc != null )
            {
                exist = eventTsc.numberValues > 0;
            }
            if(exist){
                return eventTsc;
            }else{
                return null;
            }
        }else{
            return null;
        }
    }
    private TimeSeriesContainer UpdateTimeSeries(TimeSeriesContainer input, double multiplier){
        TimeSeriesContainer outTsc = (TimeSeriesContainer)input.clone();
        double[] vals = outTsc.values;
        double[] outVals = new double[vals.length];
        _maxVal = Double.MIN_VALUE;
        for(int i = 0; i<vals.length;i++){
            outVals[i] = vals[i]*multiplier;
            if(outVals[i]>_maxVal)_maxVal = outVals[i];
        }
        outTsc.values = outVals;
        return outTsc;
    }
    protected double getOutputValue(){
        return _maxVal;
    }
    private boolean WriteTimeSeries(TimeSeriesContainer tsc, DataLocation dl,String dssFilePath){
        DSSPathname pathname = new DSSPathname(dl.getDssPath());
	pathname.setFPart(_computeOptions.getFpart());
        DSSIdentifier eventDss = new DSSIdentifier(dssFilePath,pathname.getPathname());
        eventDss.setStartTime(_computeOptions.getRunTimeWindow().getStartTime());
	eventDss.setEndTime(_computeOptions.getRunTimeWindow().getEndTime());
        tsc.fullName = pathname.getPathname();
        tsc.fileName = _computeOptions.getDssFilename();
        boolean exist = DssFileManagerImpl.getDssFileManager().exists(eventDss);
        if(exist){
            if(!_computeOptions.shouldForceCompute()){
                return true;
            }            
        }
        return 0 == DssFileManagerImpl.getDssFileManager().write(tsc);
    }
    @Override
    public boolean cancelCompute() {
        return false;
    }
    @Override
    public String getLogFile() {
        return null;
    }
    @Override
    public int getModelCount() {
        return 1;
    }

}
