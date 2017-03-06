package com.net2plan.gui.plugins.networkDesign;

import com.net2plan.gui.plugins.GUINetworkDesign;
import com.net2plan.interfaces.networkDesign.NetPlan;
import com.net2plan.interfaces.networkDesign.NetworkLayer;
import com.net2plan.utils.Triple;
import org.apache.commons.collections15.BidiMap;
import org.apache.commons.collections15.bidimap.DualHashBidiMap;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Manages the undo/redo information, tracking the current netPlan and the visualization state
 */
public class UndoRedoManager
{
    private final GUINetworkDesign callback;
    private List<TimelineState> timeline;
    private int timelineCursor;
    private int listMaxSize;

    private TimelineState backupState;

    public UndoRedoManager(GUINetworkDesign callback, int listMaxSize)
    {
        this.timeline = new ArrayList<>();
        this.timelineCursor = -1;
        this.callback = callback;
        this.listMaxSize = listMaxSize;
    }

    public void addNetPlanChange()
    {
        if (this.listMaxSize <= 1) return; // nothing is stored since nothing will be retrieved
        if (callback.inOnlineSimulationMode()) return;

        final TimelineState state = createState(callback.getDesign());

        // Removing all changes made after the one at the cursor
        if (timelineCursor != timeline.size() - 1)
        {
            timeline.subList(timelineCursor, timeline.size()).clear();

            // Adding a copy of the current state before it was modified
            timeline.add(backupState);
        }
        timeline.add(state);

        // Remove the older changes so that the list does not bloat.
        while (timeline.size() > listMaxSize)
        {
            timeline.remove(0);
            timelineCursor--;
        }

        timelineCursor++;
    }

    public void resetManager()
    {
        this.timeline.clear();
        this.timelineCursor = -1;
    }

    /**
     * Returns the undo info in the navigation. Returns null if we are already in the first element. The NetPlan object returned is null if
     * there is no change respect to the current one
     *
     * @return see above
     */
    public Triple<NetPlan, BidiMap<NetworkLayer, Integer>, Map<NetworkLayer, Boolean>> getNavigationBackElement()
    {
        if (!checkMovementValidity()) return null;
        if (timelineCursor == 0) return null;

        this.timelineCursor--;

        final TimelineState currentState = timeline.get(this.timelineCursor);
        this.backupState = createState(currentState.getStateDefinition().getFirst());

        return currentState.getStateDefinition();
    }

    /**
     * Returns the forward info in the navigation. Returns null if we are already in the head. The NetPlan object returned is null if
     * there is no change respect to the current one
     *
     * @return see above
     */
    public Triple<NetPlan, BidiMap<NetworkLayer, Integer>, Map<NetworkLayer, Boolean>> getNavigationForwardElement()
    {
        if (!checkMovementValidity()) return null;
        if (timelineCursor == timeline.size() - 1) return null;

        this.timelineCursor++;

        final TimelineState currentState = timeline.get(this.timelineCursor);
        this.backupState = createState(currentState.getStateDefinition().getFirst());

        return currentState.getStateDefinition();
    }

    private boolean checkMovementValidity()
    {
        return !(timeline.isEmpty() || this.listMaxSize <= 1 || callback.inOnlineSimulationMode());
    }

    /**
     * Creates a new timeline state from a given NetPlan moment.
     *
     * @param netPlan
     * @return
     */
    private TimelineState createState(final NetPlan netPlan)
    {
        final NetPlan npCopy = netPlan.copy();

        final BidiMap<NetworkLayer, Integer> cp_mapLayer2VisualizationOrder = new DualHashBidiMap<>();
        for (NetworkLayer cpLayer : npCopy.getNetworkLayers())
        {
            cp_mapLayer2VisualizationOrder.put(cpLayer, callback.getVisualizationState().getCanvasVisualizationOrderNotRemovingNonVisible(callback.getDesign().getNetworkLayer(cpLayer.getIndex())));
        }

        final Map<NetworkLayer, Boolean> cp_layerVisibilityMap = new HashMap<>();
        for (NetworkLayer cpLayer : npCopy.getNetworkLayers())
        {
            cp_layerVisibilityMap.put(cpLayer, callback.getVisualizationState().isLayerVisibleInCanvas(callback.getDesign().getNetworkLayer(cpLayer.getIndex())));
        }

        return new TimelineState(npCopy, cp_mapLayer2VisualizationOrder, cp_layerVisibilityMap);
    }

    private class TimelineState
    {
        private final NetPlan netPlan;
        private final BidiMap<NetworkLayer, Integer> layerOrderMap;
        private final Map<NetworkLayer, Boolean> layerVisibilityMap;

        private TimelineState(final NetPlan netPlan, final BidiMap<NetworkLayer, Integer> layerOrderMap, final Map<NetworkLayer, Boolean> layerVisibilityMap)
        {
            this.netPlan = netPlan;
            this.layerOrderMap = layerOrderMap;
            this.layerVisibilityMap = layerVisibilityMap;
        }

        private Triple<NetPlan, BidiMap<NetworkLayer, Integer>, Map<NetworkLayer, Boolean>> getStateDefinition()
        {
            return Triple.unmodifiableOf(netPlan, layerOrderMap, layerVisibilityMap);
        }
    }
}
