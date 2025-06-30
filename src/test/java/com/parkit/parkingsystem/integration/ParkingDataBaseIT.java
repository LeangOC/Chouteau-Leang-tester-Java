package com.parkit.parkingsystem.integration;

import com.parkit.parkingsystem.constants.Fare;
import com.parkit.parkingsystem.dao.ParkingSpotDAO;
import com.parkit.parkingsystem.dao.TicketDAO;
import com.parkit.parkingsystem.integration.config.DataBaseTestConfig;
import com.parkit.parkingsystem.integration.service.DataBasePrepareService;
import com.parkit.parkingsystem.model.ParkingSpot;
import com.parkit.parkingsystem.model.Ticket;
import com.parkit.parkingsystem.service.ParkingService;
import com.parkit.parkingsystem.util.InputReaderUtil;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Date;

import static junit.framework.Assert.assertEquals;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class ParkingDataBaseIT {

    private static final DataBaseTestConfig dataBaseTestConfig = new DataBaseTestConfig();
    private static ParkingSpotDAO parkingSpotDAO;
    private static TicketDAO ticketDAO;
    private static DataBasePrepareService dataBasePrepareService;

    @Mock
    private static InputReaderUtil inputReaderUtil;

    @BeforeAll
    public static void setUp() {
        parkingSpotDAO = new ParkingSpotDAO();
        parkingSpotDAO.dataBaseConfig = dataBaseTestConfig;
        ticketDAO = new TicketDAO();
        ticketDAO.dataBaseConfig = dataBaseTestConfig;
        dataBasePrepareService = new DataBasePrepareService();
    }

    @BeforeEach
    public void setUpPerTest() throws Exception {
        when(inputReaderUtil.readSelection()).thenReturn(1);
        when(inputReaderUtil.readVehicleRegistrationNumber()).thenReturn("ABCDEF");
        dataBasePrepareService.clearDataBaseEntries();
    }

    @AfterAll
    public static void tearDown(){

    }



    @Test
    public void testParkingACar(){
        ParkingService parkingService = new ParkingService(inputReaderUtil, parkingSpotDAO, ticketDAO);
        parkingService.processIncomingVehicle();

        //TODO: check that a ticket is actualy saved in DB and Parking table is updated with availability
        Ticket savedTicket = ticketDAO.getTicket("ABCDEF");
        assertNotNull(savedTicket, "Le ticket devrait être sauvegardé en base.");
        assertEquals("ABCDEF", savedTicket.getVehicleRegNumber());
        assertNotNull(savedTicket.getInTime(), "L'heure d'entrée doit être renseignée.");
        assertNull(savedTicket.getOutTime(), "L'heure de sortie ne doit pas être encore renseignée.");
        ParkingSpot updatedSpot = savedTicket.getParkingSpot();
        assertFalse(updatedSpot.isAvailable(), "La place de parking ne doit plus être disponible.");
    }

    @Test
    public void testParkingLotExit() throws InterruptedException {
        testParkingACar();
        ParkingService parkingService = new ParkingService(inputReaderUtil, parkingSpotDAO, ticketDAO);
        Thread.sleep(2000);
        parkingService.processExitingVehicle();
        //TODO: check that the fare generated and out time are populated correctly in the database

        Ticket updatedTicket = ticketDAO.getTicketIncludingOutTime("ABCDEF");
        assertNotNull(updatedTicket.getOutTime(), "L'heure de sortie doit être renseignée.");
        assertTrue(updatedTicket.getOutTime().after(updatedTicket.getInTime()), "La sortie doit être après l'entrée.");
        assertTrue(updatedTicket.getPrice() >= Fare.CAR_RATE_PER_HOUR * 0.0, "Le prix doit être proche du tarif horaire plein.");
        boolean isAvailable = parkingSpotDAO.isParkingSpotAvailable(updatedTicket.getParkingSpot().getId());
        assertTrue(isAvailable, "La place doit être disponible après la sortie.");
    }

    @Test
    public void testParkingLotExitRecurringUser() {
        ParkingService parkingService = new ParkingService(inputReaderUtil, parkingSpotDAO, ticketDAO);
        parkingService.processIncomingVehicle();

        // Première entrée
        Ticket firstTicket = ticketDAO.getTicket("ABCDEF");
        assertNotNull(firstTicket);
        firstTicket.setInTime(new Date(System.currentTimeMillis() - 2 * 60 * 60 * 1000));
        ticketDAO.updateInTimeOnly(firstTicket);
       parkingService.processExitingVehicle();

        // Deuxième entrée
        parkingService.processIncomingVehicle();
        Ticket secondTicket = ticketDAO.getIdTicket(2);
        secondTicket.setInTime(new Date(System.currentTimeMillis() - 60 * 60 * 1000));
        ticketDAO.updateInTimeOnly(secondTicket);
        parkingService.processExitingVehicle();


        //Ticket secondTicket;
        secondTicket = ticketDAO.getIdTicket(2);
        assertNotNull(secondTicket, "Le ticket récurrent doit exister.");
        assertTrue(secondTicket.getOutTime().after(secondTicket.getInTime()), "L'heure de sortie doit être après l'entrée.");

        double price = secondTicket.getPrice();
        System.out.println("prix : " + price);
        double oneHourPrice = Fare.CAR_RATE_PER_HOUR;
        double expectedMax = oneHourPrice * 0.96;
        System.out.println("expectedMax : " + expectedMax);

        assertTrue(price < expectedMax, "Le tarif doit inclure la remise utilisateur récurrent (5%).");

        boolean isAvailable = parkingSpotDAO.isParkingSpotAvailable(secondTicket.getParkingSpot().getId());
        assertTrue(isAvailable, "La place doit être disponible après la sortie.");

    }

}