package com.parkit.parkingsystem;

import com.parkit.parkingsystem.constants.ParkingType;
import com.parkit.parkingsystem.dao.ParkingSpotDAO;
import com.parkit.parkingsystem.dao.TicketDAO;
import com.parkit.parkingsystem.model.ParkingSpot;
import com.parkit.parkingsystem.model.Ticket;
import com.parkit.parkingsystem.service.ParkingService;
import com.parkit.parkingsystem.util.InputReaderUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;


import java.util.Date;

import static junit.framework.Assert.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class ParkingServiceTest {

    private ParkingService parkingService;

    @Mock
    private InputReaderUtil inputReaderUtil;

    @Mock
    private ParkingSpotDAO parkingSpotDAO;

    @Mock
    private TicketDAO ticketDAO;

    @BeforeEach
    public void setUpPerTest() {
        parkingService = new ParkingService(inputReaderUtil, parkingSpotDAO, ticketDAO);
    }

    @Test
    public void processExitingVehicleTest() {
        // GIVEN ou ARRANGE
        try {
            //Simule la lecture de la plaque d'immatriculation du véhicule : "ABCDEF"
            when(inputReaderUtil.readVehicleRegistrationNumber()).thenReturn("ABCDEF");

            //Crée une place de parking occupée (id = 1, type = voiture, non disponible)
            ParkingSpot parkingSpot = new ParkingSpot(1, ParkingType.CAR, false);

            //Crée un ticket de stationnement indiquant que le véhicule est entré il y a UNE heure.
            Ticket ticket = new Ticket();
            ticket.setInTime(new Date(System.currentTimeMillis() - 60 * 60 * 1000));
            ticket.setParkingSpot(parkingSpot);
            ticket.setVehicleRegNumber("ABCDEF");

            //Configure les comportements simulés des DAO
            when(ticketDAO.getTicket(anyString())).thenReturn(ticket);
                                           //Renvoie le ticket simulé ci-dessus.
            when(ticketDAO.updateTicket(any(Ticket.class))).thenReturn(true);
                                          //Maj du ticket
            when(parkingSpotDAO.updateParking(any(ParkingSpot.class))).thenReturn(true);
                                         //Maj de la place de parking
            when(ticketDAO.getNbTicket("ABCDEF")).thenReturn(2);
                                         //le véhicule a déjà utilisé le parking 2 fois


            // WHEN ou ACT

            parkingService.processExitingVehicle();
               // Appelle la méthode à tester : processExitingVehicle(),
                  // qui doit effectuer toute la logique de sortie d’un véhicule

            // THEN ou ASSERT

            verify(ticketDAO, times(1)).getTicket("ABCDEF");
            verify(ticketDAO, times(1)).getNbTicket("ABCDEF");
            verify(ticketDAO, times(1)).updateTicket(any(Ticket.class));
            verify(parkingSpotDAO, times(1)).updateParking(any(ParkingSpot.class));


        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException("Test setup failed");
        }
    }

    @Test
    public void
    testProcessIncomingVehicle() {
        // GIVEN
        try {
            when(inputReaderUtil.readSelection()).thenReturn(1); // 1 = CAR
            when(parkingSpotDAO.getNextAvailableSlot(ParkingType.CAR)).thenReturn(1);
            when(inputReaderUtil.readVehicleRegistrationNumber()).thenReturn("XYZ123");

            //ParkingSpot parkingSpot = new ParkingSpot(1, ParkingType.CAR, true);
            when(parkingSpotDAO.updateParking(any(ParkingSpot.class))).thenReturn(true);

            // WHEN
            parkingService.processIncomingVehicle();

            // THEN
            verify(parkingSpotDAO, times(1)).getNextAvailableSlot(ParkingType.CAR);
            verify(inputReaderUtil, times(1)).readVehicleRegistrationNumber();
            verify(ticketDAO, times(1)).saveTicket(any(Ticket.class));
            verify(parkingSpotDAO, times(1)).updateParking(any(ParkingSpot.class));

        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException("Test setup failed");
        }
    }
    @Test
    public void processExitingVehicleTestUnableUpdate() {
        // GIVEN
        try {
            when(inputReaderUtil.readVehicleRegistrationNumber()).thenReturn("ABCDEF");

            ParkingSpot parkingSpot = new ParkingSpot(1, ParkingType.CAR, false);
            Ticket ticket = new Ticket();
            ticket.setInTime(new Date(System.currentTimeMillis() - 60 * 60 * 1000)); // 1h plus tôt
            ticket.setParkingSpot(parkingSpot);
            ticket.setVehicleRegNumber("ABCDEF");

            when(ticketDAO.getTicket("ABCDEF")).thenReturn(ticket);
            when(ticketDAO.updateTicket(any(Ticket.class))).thenReturn(false); // simulate failure
            lenient().when(parkingSpotDAO.updateParking(any(ParkingSpot.class))).thenReturn(true);
            //when(parkingSpotDAO.updateParking(any(ParkingSpot.class))).thenReturn(true);
            when(ticketDAO.getNbTicket("ABCDEF")).thenReturn(1);

            // WHEN

            parkingService.processExitingVehicle();

            // THEN
            verify(ticketDAO, times(1)).getTicket("ABCDEF");
            verify(ticketDAO, times(1)).updateTicket(any(Ticket.class));
            verify(parkingSpotDAO, never()).updateParking(any(ParkingSpot.class)); // updateParking should NOT be called
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException("Test setup failed");
        }
    }
    @Test
    public void testGetNextParkingNumberIfAvailable() {
        // GIVEN
        try {
            when(inputReaderUtil.readSelection()).thenReturn(1); // 1 = CAR
            when(parkingSpotDAO.getNextAvailableSlot(ParkingType.CAR)).thenReturn(1); // spot ID 1

            parkingService = new ParkingService(inputReaderUtil, parkingSpotDAO, ticketDAO);

            // WHEN
            ParkingSpot result = parkingService.getNextParkingNumberIfAvailable();

            // THEN
            assertNotNull(result);
            assertEquals(1, result.getId());
            assertEquals(ParkingType.CAR, result.getParkingType());
            assertTrue(result.isAvailable());

            verify(parkingSpotDAO, times(1)).getNextAvailableSlot(ParkingType.CAR);
        } catch (Exception e) {
            e.printStackTrace();
            fail("Exception should not be thrown");
        }
    }
    @Test
    public void testGetNextParkingNumberIfAvailableParkingNumberNotFound() {
        // GIVEN
        try {
            when(inputReaderUtil.readSelection()).thenReturn(1); // 1 = CAR
            when(parkingSpotDAO.getNextAvailableSlot(ParkingType.CAR)).thenReturn(-1); // aucun spot disponible

            parkingService = new ParkingService(inputReaderUtil, parkingSpotDAO, ticketDAO);

            // WHEN
            ParkingSpot result = parkingService.getNextParkingNumberIfAvailable();

            // THEN
            verify(inputReaderUtil, times(1)).readSelection();
            verify(parkingSpotDAO, times(1)).getNextAvailableSlot(ParkingType.CAR);

            assertNull(result); // La méthode doit renvoyer null s'il n'y a pas de place disponible
        } catch (Exception e) {
            e.printStackTrace();
            fail("Exception should not be thrown");
        }
    }
    @Test
    public void testGetNextParkingNumberIfAvailableParkingNumberWrongArgument() {
        // GIVEN
        try {
            when(inputReaderUtil.readSelection()).thenReturn(3); // Valeur invalide

            parkingService = new ParkingService(inputReaderUtil, parkingSpotDAO, ticketDAO);

            // WHEN
            ParkingSpot result = parkingService.getNextParkingNumberIfAvailable();

            // THEN
            verify(inputReaderUtil, times(1)).readSelection();
            // Vérifie que les DAO ne sont pas appelés (aucun accès DB)
            verify(parkingSpotDAO, never()).getNextAvailableSlot(any());
            verify(ticketDAO, never()).getTicket(anyString());
            assertNull(result); // La méthode doit retourner null si choix invalide
        } catch (Exception e) {
            fail("Test should not throw an exception");
        }
    }


}
