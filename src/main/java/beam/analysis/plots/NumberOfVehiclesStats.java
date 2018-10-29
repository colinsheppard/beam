package beam.analysis.plots;

import beam.agentsim.events.PathTraversalEvent;
import com.google.common.base.CaseFormat;
import org.matsim.api.core.v01.events.Event;
import org.matsim.api.core.v01.events.PersonEntersVehicleEvent;
import org.matsim.core.controler.events.IterationEndsEvent;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.stream.Collectors;

public class NumberOfVehiclesStats implements BeamStats, IterationSummaryStats{
    private Map<String, Integer> numberOfVehiclesByType = new HashMap<>();
    private HashSet<String> uniqueVehicleIds = new HashSet<>();

    @Override
    public void processStats(Event event) {
        if (event instanceof PathTraversalEvent || event.getEventType().equalsIgnoreCase(PathTraversalEvent.EVENT_TYPE)) {
            Map<String, String> eventAttributes = event.getAttributes();
            String vehicleId = eventAttributes.get(PathTraversalEvent.ATTRIBUTE_VEHICLE_ID);
            if(uniqueVehicleIds.add(vehicleId)) {
                String vehicleType = eventAttributes.get(PathTraversalEvent.ATTRIBUTE_VEHICLE_TYPE);
                numberOfVehiclesByType.merge(vehicleType, 1, (d1, d2) -> d1 + d2);
            }
        }
    }

    @Override
    public void createGraph(IterationEndsEvent event) {

    }

    @Override
    public void resetStats() {
        numberOfVehiclesByType.clear();
    }

    @Override
    public Map<String, Double> getIterationSummaryStats() {
        return numberOfVehiclesByType.entrySet().stream().collect(Collectors.toMap(
                e -> "numberOfVehicles_" + e.getKey(),
                e -> e.getValue().doubleValue()
        )); 
    }

}
