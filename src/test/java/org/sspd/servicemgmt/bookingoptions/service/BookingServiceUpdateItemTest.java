package org.sspd.servicemgmt.bookingoptions.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.sspd.servicemgmt.bookingoptions.dto.BookingItemDTO;
import org.sspd.servicemgmt.bookingoptions.dto.BookingItemComponentDTO;
import org.sspd.servicemgmt.bookingoptions.dto.BookingItemPhotoDTO;
import org.sspd.servicemgmt.bookingoptions.model.Booking;
import org.sspd.servicemgmt.bookingoptions.model.BookingItem;
import org.sspd.servicemgmt.bookingoptions.model.BookingItemComponent;
import org.sspd.servicemgmt.bookingoptions.model.BookingItemPhoto;
import org.sspd.servicemgmt.bookingoptions.model.BookingStatus;
import org.sspd.servicemgmt.bookingoptions.repository.BookingItemRepository;
import org.sspd.servicemgmt.bookingoptions.repository.BookingRepository;
import org.sspd.servicemgmt.companysettingoptions.repository.CompanySettingsRepository;
import org.sspd.servicemgmt.customeroptions.model.Customer;
import org.sspd.servicemgmt.customeroptions.repository.CustomerRepository;
import org.sspd.servicemgmt.dataevent.DataEventPublisher;
import org.sspd.servicemgmt.servicebookingsettingsoptions.service.ServiceBookingSettingsService;
import org.sspd.servicemgmt.servicejoboptions.repository.ServiceJobRepository;
import org.sspd.servicemgmt.servicejoboptions.service.ServiceJobService;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BookingServiceUpdateItemTest {

    private BookingRepository bookingRepository;
    private BookingItemRepository itemRepository;
    private ServiceJobRepository serviceJobRepository;
    private ServiceJobService serviceJobService;
    private BookingPhotoStorageService photoStorage;
    private ServiceBookingSettingsService bookingSettingsService;
    private BookingService service;

    @BeforeEach
    void setUp() {
        bookingRepository = mock(BookingRepository.class);
        itemRepository = mock(BookingItemRepository.class);
        serviceJobRepository = mock(ServiceJobRepository.class);
        serviceJobService = mock(ServiceJobService.class);
        photoStorage = mock(BookingPhotoStorageService.class);
        bookingSettingsService = mock(ServiceBookingSettingsService.class);
        when(bookingSettingsService.getMaxPhotosPerItem()).thenReturn(50);
        service = new BookingService(
                bookingRepository,
                itemRepository,
                mock(CustomerRepository.class),
                mock(CompanySettingsRepository.class),
                serviceJobRepository,
                serviceJobService,
                mock(DataEventPublisher.class),
                photoStorage,
                bookingSettingsService
        );
        when(serviceJobService.findByBookingId(anyInt())).thenReturn(List.of());
        when(itemRepository.save(any(BookingItem.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void rejectsPhotoChangesWhenItemAlreadyConverted() {
        Fixture fx = fixture(true);
        BookingItemDTO dto = baseDto(fx.item.getItemName());
        dto.setPhotos(List.of(dataUrlPhoto(1, "data:image/jpeg;base64,abc")));

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> service.updateItem(10, 5, dto));
        assertTrue(ex.getMessage().toLowerCase().contains("photo"));
        verify(photoStorage, never()).store(any(), anyInt(), anyInt());
        verify(itemRepository, never()).save(any());
    }

    @Test
    void rejectsPathOnlyEntryWhenSlotDoesNotExist() {
        Fixture fx = fixture(false);
        BookingItemDTO dto = baseDto("Phone");
        BookingItemPhotoDTO hijack = new BookingItemPhotoDTO();
        hijack.setSlot(1);
        hijack.setImagePath("/uploads/booking-photos/booking-items/999/stolen.webp");
        hijack.setThumbnailPath("/uploads/booking-photos/booking-items/999/stolen-thumb.webp");
        dto.setPhotos(List.of(hijack));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.updateItem(10, 5, dto));
        assertTrue(ex.getMessage().toLowerCase().contains("dataurl"));
        verify(photoStorage, never()).store(any(), anyInt(), anyInt());
        verify(photoStorage, never()).deleteExisting(any(), any());
        assertTrue(fx.item.getPhotos().isEmpty());
    }

    @Test
    void addsPhotoViaDataUrl() {
        Fixture fx = fixture(false);
        when(photoStorage.store(eq("data:image/jpeg;base64,new"), eq(10), eq(1)))
                .thenReturn(new BookingPhotoStorageService.StoredPhoto(
                        "/uploads/booking-photos/booking-items/10/new.webp",
                        "/uploads/booking-photos/booking-items/10/new-thumb.webp"));

        BookingItemDTO dto = baseDto("Phone");
        dto.setPhotos(List.of(dataUrlPhoto(1, "data:image/jpeg;base64,new")));

        service.updateItem(10, 5, dto);

        assertEquals(1, fx.item.getPhotos().size());
        assertEquals(1, fx.item.getPhotos().get(0).getSlot());
        assertEquals("/uploads/booking-photos/booking-items/10/new.webp",
                fx.item.getPhotos().get(0).getImagePath());
        verify(photoStorage).store("data:image/jpeg;base64,new", 10, 1);
        // No prior file to delete on commit when adding a brand-new slot.
        verify(photoStorage, never()).deleteExisting(any(), any());
        verify(itemRepository).save(fx.item);
    }

    @Test
    void replacesExistingSlotAndDeletesOldFileWhenNoActiveTransaction() {
        Fixture fx = fixture(false);
        BookingItemPhoto old = BookingItemPhoto.builder()
                .id(1)
                .bookingItem(fx.item)
                .slot(1)
                .imagePath("/uploads/booking-photos/booking-items/10/old.webp")
                .thumbnailPath("/uploads/booking-photos/booking-items/10/old-thumb.webp")
                .build();
        fx.item.getPhotos().add(old);

        when(photoStorage.store(eq("data:image/jpeg;base64,repl"), eq(10), eq(1)))
                .thenReturn(new BookingPhotoStorageService.StoredPhoto(
                        "/uploads/booking-photos/booking-items/10/repl.webp",
                        "/uploads/booking-photos/booking-items/10/repl-thumb.webp"));

        BookingItemDTO dto = baseDto("Phone");
        dto.setPhotos(List.of(dataUrlPhoto(1, "data:image/jpeg;base64,repl")));

        service.updateItem(10, 5, dto);

        assertEquals(1, fx.item.getPhotos().size());
        assertEquals("/uploads/booking-photos/booking-items/10/repl.webp",
                fx.item.getPhotos().get(0).getImagePath());
        // Without an active Spring transaction, commit-cleanup runs immediately.
        verify(photoStorage).deleteExisting(
                "/uploads/booking-photos/booking-items/10/old.webp",
                "/uploads/booking-photos/booking-items/10/old-thumb.webp");
    }

    @Test
    void doesNotDeleteOldFileWhenStoreFailsDuringReplace() {
        Fixture fx = fixture(false);
        BookingItemPhoto old = BookingItemPhoto.builder()
                .id(1)
                .bookingItem(fx.item)
                .slot(1)
                .imagePath("/uploads/booking-photos/booking-items/10/old.webp")
                .thumbnailPath("/uploads/booking-photos/booking-items/10/old-thumb.webp")
                .build();
        fx.item.getPhotos().add(old);

        when(photoStorage.store(any(), anyInt(), anyInt()))
                .thenThrow(new IllegalStateException("disk full"));

        BookingItemDTO dto = baseDto("Phone");
        dto.setPhotos(List.of(dataUrlPhoto(1, "data:image/jpeg;base64,fail")));

        assertThrows(IllegalStateException.class, () -> service.updateItem(10, 5, dto));
        assertEquals("/uploads/booking-photos/booking-items/10/old.webp", old.getImagePath());
        verify(photoStorage, never()).deleteExisting(any(), any());
        verify(itemRepository, never()).save(any());
    }

    @Test
    void deletesOmittedSlotFileWhenNoActiveTransaction() {
        Fixture fx = fixture(false);
        BookingItemPhoto keep = BookingItemPhoto.builder()
                .id(1)
                .bookingItem(fx.item)
                .slot(1)
                .imagePath("/uploads/booking-photos/booking-items/10/keep.webp")
                .thumbnailPath("/uploads/booking-photos/booking-items/10/keep-thumb.webp")
                .build();
        BookingItemPhoto drop = BookingItemPhoto.builder()
                .id(2)
                .bookingItem(fx.item)
                .slot(2)
                .imagePath("/uploads/booking-photos/booking-items/10/drop.webp")
                .thumbnailPath("/uploads/booking-photos/booking-items/10/drop-thumb.webp")
                .build();
        fx.item.getPhotos().add(keep);
        fx.item.getPhotos().add(drop);

        BookingItemDTO dto = baseDto("Phone");
        BookingItemPhotoDTO keepDto = new BookingItemPhotoDTO();
        keepDto.setSlot(1);
        keepDto.setImagePath(keep.getImagePath());
        keepDto.setThumbnailPath(keep.getThumbnailPath());
        dto.setPhotos(List.of(keepDto));

        service.updateItem(10, 5, dto);

        assertEquals(1, fx.item.getPhotos().size());
        assertEquals(1, fx.item.getPhotos().get(0).getSlot());
        verify(photoStorage).deleteExisting(
                "/uploads/booking-photos/booking-items/10/drop.webp",
                "/uploads/booking-photos/booking-items/10/drop-thumb.webp");
        verify(photoStorage, never()).store(any(), anyInt(), anyInt());
    }

    @Test
    void keepsExistingSlotWithoutAcceptingClientPathOverride() {
        Fixture fx = fixture(false);
        BookingItemPhoto existing = BookingItemPhoto.builder()
                .id(1)
                .bookingItem(fx.item)
                .slot(1)
                .imagePath("/uploads/booking-photos/booking-items/10/real.webp")
                .thumbnailPath("/uploads/booking-photos/booking-items/10/real-thumb.webp")
                .build();
        fx.item.getPhotos().add(existing);

        BookingItemDTO dto = baseDto("Phone");
        BookingItemPhotoDTO keep = new BookingItemPhotoDTO();
        keep.setSlot(1);
        keep.setImagePath("/uploads/booking-photos/booking-items/999/hijack.webp");
        keep.setThumbnailPath("/uploads/booking-photos/booking-items/999/hijack-thumb.webp");
        dto.setPhotos(List.of(keep));

        service.updateItem(10, 5, dto);

        assertEquals("/uploads/booking-photos/booking-items/10/real.webp", existing.getImagePath());
        verify(photoStorage, never()).store(any(), anyInt(), anyInt());
        verify(photoStorage, never()).deleteExisting(any(), any());
    }

    @Test
    void acceptsMoreThanThreeExistingItemPhotos() {
        Fixture fx = fixture(false);
        List<BookingItemPhotoDTO> incoming = new ArrayList<>();
        for (int slot = 1; slot <= 4; slot++) {
            BookingItemPhoto existing = BookingItemPhoto.builder()
                    .id(slot)
                    .bookingItem(fx.item)
                    .slot(slot)
                    .imagePath("/uploads/booking-photos/booking-items/10/photo-" + slot + ".webp")
                    .thumbnailPath("/uploads/booking-photos/booking-items/10/photo-" + slot + "-thumb.webp")
                    .build();
            fx.item.getPhotos().add(existing);
            BookingItemPhotoDTO keep = new BookingItemPhotoDTO();
            keep.setSlot(slot);
            keep.setImagePath(existing.getImagePath());
            keep.setThumbnailPath(existing.getThumbnailPath());
            incoming.add(keep);
        }
        BookingItemDTO dto = baseDto("Desktop");
        dto.setPhotos(incoming);

        service.updateItem(10, 5, dto);

        assertEquals(4, fx.item.getPhotos().size());
        verify(photoStorage, never()).store(any(), anyInt(), anyInt());
        verify(photoStorage, never()).deleteExisting(any(), any());
    }

    @Test
    void replacesStructuredComponentsAndNormalizesType() {
        Fixture fx = fixture(false);
        fx.item.getComponents().add(BookingItemComponent.builder()
                .bookingItem(fx.item)
                .componentType("CPU")
                .specification("Old CPU")
                .quantity(1)
                .build());

        BookingItemDTO dto = baseDto("Desktop");
        BookingItemComponentDTO ram = new BookingItemComponentDTO();
        ram.setComponentType("ram module");
        ram.setBrand("Kingston");
        ram.setSpecification("DDR4 16GB 3200MHz");
        ram.setSerialNo("RAM-001");
        ram.setQuantity(2);
        ram.setConditionNote("Both detected");
        dto.setComponents(List.of(ram));

        service.updateItem(10, 5, dto);

        assertEquals(1, fx.item.getComponents().size());
        BookingItemComponent saved = fx.item.getComponents().get(0);
        assertEquals("RAM_MODULE", saved.getComponentType());
        assertEquals("Kingston", saved.getBrand());
        assertEquals("DDR4 16GB 3200MHz", saved.getSpecification());
        assertEquals("RAM-001", saved.getSerialNo());
        assertEquals(2, saved.getQuantity());
        assertEquals("Both detected", saved.getConditionNote());
        assertEquals(fx.item, saved.getBookingItem());
    }

    @Test
    void preservesComponentsWhenLegacyClientOmitsField() {
        Fixture fx = fixture(false);
        BookingItemComponent cpu = BookingItemComponent.builder()
                .bookingItem(fx.item)
                .componentType("CPU")
                .specification("Core i5")
                .quantity(1)
                .build();
        fx.item.getComponents().add(cpu);

        service.updateItem(10, 5, baseDto("Desktop"));

        assertEquals(List.of(cpu), fx.item.getComponents());
    }

    @Test
    void rejectsInvalidComponentQuantity() {
        Fixture fx = fixture(false);
        BookingItemDTO dto = baseDto("Desktop");
        BookingItemComponentDTO component = new BookingItemComponentDTO();
        component.setComponentType("RAM");
        component.setQuantity(0);
        dto.setComponents(List.of(component));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.updateItem(10, 5, dto));

        assertTrue(ex.getMessage().contains("between 1 and 100"));
        verify(itemRepository, never()).save(any());
        assertTrue(fx.item.getComponents().isEmpty());
    }

    private Fixture fixture(boolean converted) {
        Customer customer = new Customer();
        customer.setId(1);
        customer.setName("Alice");
        customer.setPhone("09");

        Booking booking = Booking.builder()
                .id(10)
                .status(BookingStatus.ARRIVED)
                .customer(customer)
                .items(new ArrayList<>())
                .build();
        BookingItem item = BookingItem.builder()
                .id(5)
                .booking(booking)
                .itemName("Phone")
                .convertedJobId(converted ? 99 : null)
                .photos(new ArrayList<>())
                .components(new ArrayList<>())
                .build();
        booking.getItems().add(item);

        when(bookingRepository.findByIdForUpdate(10)).thenReturn(Optional.of(booking));
        when(itemRepository.findByIdAndBookingId(5, 10)).thenReturn(Optional.of(item));
        return new Fixture(booking, item);
    }

    private static BookingItemDTO baseDto(String name) {
        BookingItemDTO dto = new BookingItemDTO();
        dto.setItemName(name);
        return dto;
    }

    private static BookingItemPhotoDTO dataUrlPhoto(int slot, String dataUrl) {
        BookingItemPhotoDTO photo = new BookingItemPhotoDTO();
        photo.setSlot(slot);
        photo.setDataUrl(dataUrl);
        return photo;
    }

    private record Fixture(Booking booking, BookingItem item) {}
}
