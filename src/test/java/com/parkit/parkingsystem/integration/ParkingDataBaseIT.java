package com.parkit.parkingsystem.integration;

import com.parkit.parkingsystem.constants.Fare;
import com.parkit.parkingsystem.constants.ParkingType;
import com.parkit.parkingsystem.dao.ParkingSpotDAO;
import com.parkit.parkingsystem.dao.TicketDAO;
import com.parkit.parkingsystem.integration.config.DataBaseTestConfig;
import com.parkit.parkingsystem.integration.service.DataBasePrepareService;
import com.parkit.parkingsystem.model.ParkingSpot;
import com.parkit.parkingsystem.model.Ticket;
import com.parkit.parkingsystem.service.ParkingService;
import com.parkit.parkingsystem.util.InputReaderUtil;

import org.junit.jupiter.api.*;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class ParkingDataBaseIT {

    private static DataBaseTestConfig dataBaseTestConfig = new DataBaseTestConfig();
    private static ParkingSpotDAO parkingSpotDAO;
    private static TicketDAO ticketDAO;
    private static DataBasePrepareService dataBasePrepareService;
    private static ParkingService parkingService;

    private static InputReaderUtil inputReaderUtil;

    @BeforeAll
    public static void setUp() {
        inputReaderUtil = mock(InputReaderUtil.class);
        parkingSpotDAO = new ParkingSpotDAO();
        ticketDAO = new TicketDAO();
        dataBasePrepareService = new DataBasePrepareService();

        parkingService = new ParkingService(inputReaderUtil, parkingSpotDAO, ticketDAO);

        parkingSpotDAO.dataBaseConfig = dataBaseTestConfig;
        ticketDAO.dataBaseConfig = dataBaseTestConfig;
    }

    @BeforeEach
    public void setUpPerTest() throws Exception {
        dataBasePrepareService.clearDataBaseEntries();
        when(inputReaderUtil.readVehicleRegistrationNumber()).thenReturn("ABCDEF");
        when(inputReaderUtil.readSelection()).thenReturn(1); // 1 = CAR
    }

    @Test
    public void testParkingACar() {
        parkingService.processIncomingVehicle();

        Ticket savedTicket = ticketDAO.getTicket("ABCDEF");
        assertNotNull(savedTicket, "Le ticket doit avoir été sauvegardé.");
        assertNotNull(savedTicket.getInTime(), "L'heure d'entrée doit être renseignée.");
        assertNull(savedTicket.getOutTime(), "L'heure de sortie ne doit pas être encore définie.");
        assertEquals("ABCDEF", savedTicket.getVehicleRegNumber());

        ParkingSpot savedSpot = savedTicket.getParkingSpot();
        assertNotNull(savedSpot, "La place de parking doit être associée.");
        assertFalse(savedSpot.isAvailable(), "La place doit être marquée comme occupée.");

        // Vérification dans la BDD
        boolean isAvailable = parkingSpotDAO.isParkingSpotAvailable(savedSpot.getId());
        assertFalse(isAvailable, "La place ne doit pas être disponible après l'entrée du véhicule.");
    }

    @Test
    public void testParkingLotExit() throws Exception {
        // Étape 1 : Faire entrer le véhicule
        parkingService.processIncomingVehicle();

        // Étape 2 : Forcer inTime à 1h avant avec une requête SQL directe
        try (Connection con = dataBaseTestConfig.getConnection();
             PreparedStatement ps = con.prepareStatement("UPDATE ticket SET in_time = ? WHERE vehicle_reg_number = ?")) {
            ps.setTimestamp(1, new java.sql.Timestamp(System.currentTimeMillis() - 60 * 60 * 1000)); // -1h
            ps.setString(2, "ABCDEF");
            ps.executeUpdate();
        }

        // Étape 3 : Sortie du véhicule
        parkingService.processExitingVehicle();

        // Étape 4 : Vérifications
        Ticket updatedTicket = ticketDAO.getTicket("ABCDEF");
        assertNotNull(updatedTicket.getOutTime(), "L'heure de sortie doit être définie.");
        assertTrue(updatedTicket.getOutTime().after(updatedTicket.getInTime()), "La sortie doit être après l'entrée.");
        assertNotNull(updatedTicket.getPrice(), "Le prix doit être défini.");
        assertTrue(updatedTicket.getPrice() >= Fare.CAR_RATE_PER_HOUR * 0.99, "Le prix doit être proche du tarif horaire plein.");

        boolean isAvailable = parkingSpotDAO.isParkingSpotAvailable(updatedTicket.getParkingSpot().getId());
        assertTrue(isAvailable, "La place doit être disponible après la sortie.");
    }

    @Test
    public void testParkingLotExitRecurringUser() throws Exception {
        String vehicleRegNumber = "RECUR123";

        // Première entrée-sortie
        when(inputReaderUtil.readVehicleRegistrationNumber()).thenReturn(vehicleRegNumber);
        parkingService.processIncomingVehicle();
        Thread.sleep(1000);
        parkingService.processExitingVehicle();

        // Deuxième entrée-sortie (utilisateur récurrent)
        parkingService.processIncomingVehicle();
        Thread.sleep(1000);
        parkingService.processExitingVehicle();

        Ticket secondTicket = ticketDAO.getTicket(vehicleRegNumber);
        assertNotNull(secondTicket, "Le ticket récurrent doit exister.");
        assertNotNull(secondTicket.getPrice(), "Le tarif doit être renseigné.");

        double price = secondTicket.getPrice();
        double oneHourPrice = Fare.CAR_RATE_PER_HOUR;
        double expectedMax = oneHourPrice * 0.95;

        assertTrue(price <= expectedMax, "Le tarif doit inclure la remise utilisateur récurrent.");

        boolean isAvailable = parkingSpotDAO.isParkingSpotAvailable(secondTicket.getParkingSpot().getId());
        assertTrue(isAvailable, "La place doit être libre après la sortie.");
    }
}
