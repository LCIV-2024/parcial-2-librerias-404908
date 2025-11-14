package com.example.libreria.service;

import com.example.libreria.dto.ReservationRequestDTO;
import com.example.libreria.dto.ReservationResponseDTO;
import com.example.libreria.dto.ReturnBookRequestDTO;
import com.example.libreria.model.Book;
import com.example.libreria.model.Reservation;
import com.example.libreria.model.User;
import com.example.libreria.repository.BookRepository;
import com.example.libreria.repository.ReservationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReservationServiceTest {
    
    @Mock
    private ReservationRepository reservationRepository;
    
    @Mock
    private BookRepository bookRepository;
    
    @Mock
    private BookService bookService;
    
    @Mock
    private UserService userService;
    
    @InjectMocks
    private ReservationService reservationService;
    
    private User testUser;
    private Book testBook;
    private Reservation testReservation;
    
    @BeforeEach
    void setUp() {
        testUser = new User();
        testUser.setId(1L);
        testUser.setName("Juan Pérez");
        testUser.setEmail("juan@example.com");
        
        testBook = new Book();
        testBook.setExternalId(258027L);
        testBook.setTitle("The Lord of the Rings");
        testBook.setPrice(new BigDecimal("15.99"));
        testBook.setStockQuantity(10);
        testBook.setAvailableQuantity(5);
        
        testReservation = new Reservation();
        testReservation.setId(1L);
        testReservation.setUser(testUser);
        testReservation.setBook(testBook);
        testReservation.setRentalDays(7);
        testReservation.setStartDate(LocalDate.now());
        testReservation.setExpectedReturnDate(LocalDate.now().plusDays(7));
        testReservation.setDailyRate(new BigDecimal("15.99"));
        testReservation.setTotalFee(new BigDecimal("111.93"));
        testReservation.setStatus(Reservation.ReservationStatus.ACTIVE);
        testReservation.setCreatedAt(LocalDateTime.now());
    }

    @Test
    void testCreateReservation_Success() {
        ReservationRequestDTO request = new ReservationRequestDTO();
        request.setUserId(1L);
        request.setBookExternalId(258027L);
        request.setRentalDays(7);
        request.setStartDate(LocalDate.now());

        Mockito.when(userService.getUserEntity(1L)).thenReturn(testUser);
        Mockito.when(bookRepository.findByExternalId(258027L)).thenReturn(Optional.of(testBook));
        Mockito.when(reservationRepository.save(Mockito.any(Reservation.class))).thenReturn(testReservation);

        ReservationResponseDTO response = reservationService.createReservation(request);

        assertNotNull(response);
        assertEquals(1L, response.getId());
        assertEquals(1L, response.getUserId());
        assertEquals(258027L, response.getBookExternalId());
        assertEquals(Reservation.ReservationStatus.ACTIVE, response.getStatus());

        Mockito.verify(bookService).decreaseAvailableQuantity(258027L);
        Mockito.verify(reservationRepository).save(Mockito.any(Reservation.class));
    }



    @Test
    void testCreateReservation_BookNotAvailable() {
        ReservationRequestDTO request = new ReservationRequestDTO();
        request.setUserId(1L);
        request.setBookExternalId(258027L);
        request.setRentalDays(5);
        request.setStartDate(LocalDate.now());

        testBook.setAvailableQuantity(0);

        Mockito.when(bookRepository.findByExternalId(258027L))
                .thenReturn(Optional.of(testBook));

        assertThrows(RuntimeException.class,
                () -> reservationService.createReservation(request));
    }



    @Test
    void testReturnBook_OnTime() {
        LocalDate expected = LocalDate.now().plusDays(7);
        testReservation.setExpectedReturnDate(expected);

        ReturnBookRequestDTO request = new ReturnBookRequestDTO();
        request.setReturnDate(expected);

        Mockito.when(reservationRepository.findById(1L))
                .thenReturn(Optional.of(testReservation));

        Mockito.when(reservationRepository.save(Mockito.any()))
                .thenAnswer(invocation -> invocation.getArgument(0));

        ReservationResponseDTO response = reservationService.returnBook(1L, request);

        assertEquals(expected, response.getActualReturnDate());
        assertEquals(BigDecimal.ZERO, response.getLateFee());
        assertEquals(Reservation.ReservationStatus.RETURNED, response.getStatus());
    }


    @Test
    void testReturnBook_Overdue() {
        LocalDate expected = LocalDate.now().plusDays(7);
        LocalDate actual = expected.plusDays(3);
        testReservation.setExpectedReturnDate(expected);

        ReturnBookRequestDTO request = new ReturnBookRequestDTO();
        request.setReturnDate(actual);

        Mockito.when(reservationRepository.findById(1L))
                .thenReturn(Optional.of(testReservation));

        Mockito.when(reservationRepository.save(Mockito.any()))
                .thenAnswer(invocation -> invocation.getArgument(0));

        ReservationResponseDTO response = reservationService.returnBook(1L, request);

        BigDecimal expectedLateFee = testBook.getPrice()
                .multiply(new BigDecimal("0.15"))
                .multiply(new BigDecimal(3))
                .setScale(2, RoundingMode.HALF_UP);

        assertEquals(actual, response.getActualReturnDate());
        assertEquals(expectedLateFee, response.getLateFee());
        assertEquals(Reservation.ReservationStatus.OVERDUE, response.getStatus());
    }


    @Test
    void testGetReservationById_Success() {
        when(reservationRepository.findById(1L)).thenReturn(Optional.of(testReservation));
        
        ReservationResponseDTO result = reservationService.getReservationById(1L);
        
        assertNotNull(result);
        assertEquals(testReservation.getId(), result.getId());
    }

    @Test
    void testGetAllReservations() {
        when(reservationRepository.findAll()).thenReturn(List.of(testReservation));
        List<ReservationResponseDTO> result = reservationService.getAllReservations();
        assertNotNull(result);
        assertEquals(1, result.size());
    }

    @Test
    void testGetReservationsByUserId() {
        when(reservationRepository.findByUserId(1L)).thenReturn(Arrays.asList(testReservation));
        
        List<ReservationResponseDTO> result = reservationService.getReservationsByUserId(1L);
        
        assertNotNull(result);
        assertEquals(1, result.size());
    }
    
    @Test
    void testGetActiveReservations() {
        when(reservationRepository.findByStatus(Reservation.ReservationStatus.ACTIVE))
                .thenReturn(Arrays.asList(testReservation));
        
        List<ReservationResponseDTO> result = reservationService.getActiveReservations();
        
        assertNotNull(result);
        assertEquals(1, result.size());
    }
}

