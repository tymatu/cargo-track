package com.cargotrack.demo;

import com.cargotrack.TestcontainersConfiguration;
import com.cargotrack.parcel.ParcelRepository;
import com.cargotrack.parcel.TrackingEventRepository;
import com.cargotrack.shipment.ShipmentRepository;
import com.cargotrack.shipment.ShipmentStatus;
import com.cargotrack.truck.TruckRepository;
import com.cargotrack.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "app.demo.life-enabled=false",
        "app.routing.osrm-enabled=false",
        "app.simulation.enabled=false"
})
@ActiveProfiles("dev")
@Import(TestcontainersConfiguration.class)
class DevDataSeederTest {

    @Autowired
    private DevDataSeeder seeder;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private TruckRepository truckRepository;
    @Autowired
    private ParcelRepository parcelRepository;
    @Autowired
    private TrackingEventRepository trackingEventRepository;
    @Autowired
    private ShipmentRepository shipmentRepository;

    @Test
    void seedCreatesLivingDemoAndIsIdempotent() {
        assertThat(userRepository.findByEmail(DevDataSeeder.DEMO_USER_EMAIL)).isPresent();
        assertThat(userRepository.findByEmail(DevDataSeeder.DEMO_ADMIN_EMAIL)).isPresent();
        assertThat(truckRepository.count()).isEqualTo(10);
        assertThat(parcelRepository.count()).isEqualTo(40);
        assertThat(trackingEventRepository.count()).isGreaterThan(40);
        assertThat(shipmentRepository.countByStatus(ShipmentStatus.IN_TRANSIT)).isEqualTo(3);

        long users = userRepository.count();
        long trucks = truckRepository.count();
        long parcels = parcelRepository.count();
        long shipments = shipmentRepository.count();
        seeder.run(new DefaultApplicationArguments(new String[0]));

        assertThat(userRepository.count()).isEqualTo(users);
        assertThat(truckRepository.count()).isEqualTo(trucks);
        assertThat(parcelRepository.count()).isEqualTo(parcels);
        assertThat(shipmentRepository.count()).isEqualTo(shipments);
    }
}
