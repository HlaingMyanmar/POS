package org.sspd.servicemgmt.bookingoptions.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.sspd.servicemgmt.bookingoptions.dto.BookingDTO;
import org.sspd.servicemgmt.bookingoptions.dto.BookingItemDTO;
import org.sspd.servicemgmt.bookingoptions.dto.BookingItemPhotoDTO;
import org.sspd.servicemgmt.bookingoptions.model.Booking;
import org.sspd.servicemgmt.bookingoptions.model.BookingItem;
import org.sspd.servicemgmt.bookingoptions.model.BookingItemPhoto;
import org.sspd.servicemgmt.bookingoptions.model.BookingStatus;
import org.sspd.servicemgmt.bookingoptions.repository.BookingItemRepository;
import org.sspd.servicemgmt.bookingoptions.repository.BookingRepository;
import org.sspd.servicemgmt.companysettingoptions.repository.CompanySettingsRepository;
import org.sspd.servicemgmt.customeroptions.repository.CustomerRepository;
import org.sspd.servicemgmt.exceptionhandler.ResourceNotFoundException;
import org.sspd.servicemgmt.servicejoboptions.dto.ServiceJobDTO;
import org.sspd.servicemgmt.servicejoboptions.model.ServiceMode;
import org.sspd.servicemgmt.servicejoboptions.repository.ServiceJobRepository;
import org.sspd.servicemgmt.servicejoboptions.service.ServiceJobService;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class BookingService {
    private static final int MAX_PHOTOS_PER_ITEM = 3;
    private static final int MAX_PHOTO_DATA_URL_LENGTH = 4_500_000;

    private final BookingRepository repository;
    private final BookingItemRepository itemRepository;
    private final CustomerRepository customerRepository;
    private final CompanySettingsRepository companySettingsRepository;
    private final ServiceJobRepository serviceJobRepository;
    private final ServiceJobService serviceJobService;
    private final SimpMessagingTemplate messagingTemplate;
    private final BookingPhotoStorageService bookingPhotoStorageService;

    @Transactional(readOnly = true)
    public Page<BookingDTO> findAll(String search, String dateFrom, String dateTo, int page, int size) {
        return repository.search(
                search == null ? "" : search.trim(),
                parseDate(dateFrom),
                parseDate(dateTo),
                PageRequest.of(Math.max(0, page), Math.max(1, Math.min(size, 500)),
                        Sort.by(Sort.Direction.DESC, "id")))
                .map(booking -> toDto(booking, false));
    }

    @Transactional
    public BookingDTO findById(Integer id) {
        Booking booking = require(id);
        migrateLegacyPhotos(booking);
        return toDto(booking, true);
    }

    @Transactional
    public BookingDTO create(BookingDTO dto) {
        validateBase(dto);
        Booking booking = Booking.builder()
                .bookingNo("TMP-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12))
                .customer(customerRepository.findById(dto.getCustomerId())
                        .orElseThrow(() -> new ResourceNotFoundException("Customer not found")))
                .bookingDate(dto.getBookingDate() == null ? LocalDate.now() : dto.getBookingDate())
                .appointmentDate(dto.getAppointmentDate())
                .complaintNote(trimToNull(dto.getComplaintNote()))
                .status(BookingStatus.CONFIRMED)
                .remark(trimToNull(dto.getRemark()))
                .items(new ArrayList<>())
                .build();
        booking = repository.saveAndFlush(booking);
        booking.setBookingNo(generateBookingNo(booking.getId()));
        BookingDTO result = toDto(repository.save(booking), true);
        broadcast("BOOKING_CREATED");
        return result;
    }

    @Transactional
    public BookingDTO update(Integer id, BookingDTO dto) {
        Booking booking = requireForUpdate(id);
        if (booking.getStatus() == BookingStatus.CANCELED)
            throw new IllegalStateException("Canceled booking cannot be edited");
        if (isFullyConverted(booking))
            throw new IllegalStateException("Fully converted booking cannot be edited");
        validateBase(dto);
        booking.setCustomer(customerRepository.findById(dto.getCustomerId())
                .orElseThrow(() -> new ResourceNotFoundException("Customer not found")));
        booking.setBookingDate(dto.getBookingDate() == null ? booking.getBookingDate() : dto.getBookingDate());
        booking.setAppointmentDate(dto.getAppointmentDate());
        booking.setComplaintNote(trimToNull(dto.getComplaintNote()));
        booking.setRemark(trimToNull(dto.getRemark()));
        BookingDTO result = toDto(repository.save(booking), true);
        broadcast("BOOKING_UPDATED");
        return result;
    }

    @Transactional
    public BookingDTO cancel(Integer id) {
        Booking booking = requireForUpdate(id);
        if (booking.getStatus() == BookingStatus.CANCELED) return toDto(booking, true);
        if (!serviceJobRepository.findAllByBookingIdOrderByIdAsc(id).isEmpty())
            throw new IllegalStateException("Booking with linked service jobs cannot be canceled");
        booking.setStatus(BookingStatus.CANCELED);
        BookingDTO result = toDto(repository.save(booking), true);
        broadcast("BOOKING_CANCELED");
        return result;
    }

    @Transactional
    public BookingDTO addItems(Integer id, List<BookingItemDTO> itemDtos) {
        Booking booking = requireForUpdate(id);
        if (booking.getStatus() == BookingStatus.CANCELED)
            throw new IllegalStateException("Canceled booking cannot receive items");
        if (!serviceJobRepository.findAllByBookingIdOrderByIdAsc(id).isEmpty())
            throw new IllegalStateException("Booking already converted to a service job");
        if (itemDtos == null || itemDtos.isEmpty())
            throw new IllegalArgumentException("At least one item is required");

        for (BookingItemDTO dto : itemDtos) {
            String itemName = trimToNull(dto.getItemName());
            if (itemName == null) throw new IllegalArgumentException("Item name is required");
            BookingItem item = BookingItem.builder()
                    .booking(booking)
                    .itemName(itemName)
                    .deviceType(trimToNull(dto.getDeviceType()))
                    .serialNo(trimToNull(dto.getSerialNo()))
                    .color(trimToNull(dto.getColor()))
                    .accessories(trimToNull(dto.getAccessories()))
                    .problemDesc(firstNonBlank(dto.getProblemDesc(), booking.getComplaintNote()))
                    .itemCondition(trimToNull(dto.getItemCondition()))
                    .noticed(trimToNull(dto.getNoticed()))
                    .photos(new ArrayList<>())
                    .build();
            attachPhotos(item, dto.getPhotos());
            booking.getItems().add(item);
        }
        booking.setStatus(BookingStatus.ARRIVED);
        BookingDTO result = toDto(repository.save(booking), true);
        broadcast("BOOKING_ITEMS_RECEIVED");
        return result;
    }

    @Transactional
    public BookingDTO removeItem(Integer bookingId, Integer itemId) {
        Booking booking = requireForUpdate(bookingId);
        if (booking.getStatus() == BookingStatus.CANCELED)
            throw new IllegalStateException("Canceled booking cannot be edited");
        BookingItem item = itemRepository.findByIdAndBookingId(itemId, bookingId)
                .orElseThrow(() -> new ResourceNotFoundException("Booking item not found"));
        if (item.getConvertedJobId() != null)
            throw new IllegalStateException("Converted booking item cannot be removed");
        item.getPhotos().forEach(photo -> bookingPhotoStorageService.deleteExisting(
            photo.getImagePath(), photo.getThumbnailPath()));
        booking.getItems().removeIf(existing -> existing.getId().equals(itemId));
        itemRepository.delete(item);
        if (booking.getItems().isEmpty()) booking.setStatus(BookingStatus.CONFIRMED);
        BookingDTO result = toDto(repository.save(booking), true);
        broadcast("BOOKING_ITEM_REMOVED");
        return result;
    }

    @Transactional
    public BookingDTO convertOutdoor(Integer id) {
        Booking booking = requireForUpdate(id);
        if (booking.getStatus() != BookingStatus.CONFIRMED)
            throw new IllegalStateException("Only CONFIRMED booking can be converted to an outdoor job");
        if (serviceJobRepository.existsByBookingIdAndServiceMode(id, ServiceMode.OUTDOOR))
            throw new IllegalStateException("Outdoor service job already exists for this booking");

        ServiceJobDTO request = baseJob(booking, ServiceMode.OUTDOOR);
        request.setItemName(outdoorItemName(booking));
        request.setProblemDesc(booking.getComplaintNote());
        serviceJobService.create(request);
        BookingDTO result = toDto(booking, true);
        broadcast("BOOKING_OUTDOOR_CONVERTED");
        return result;
    }

    @Transactional
    public BookingDTO convertIndoor(Integer id) {
        Booking booking = requireForUpdate(id);
        if (booking.getStatus() != BookingStatus.ARRIVED)
            throw new IllegalStateException("Only ARRIVED booking can be converted to indoor jobs");
        List<BookingItem> pending = booking.getItems().stream()
                .filter(item -> item.getConvertedJobId() == null)
                .toList();
        if (pending.isEmpty())
            throw new IllegalStateException("No unconverted booking items");

        for (BookingItem item : pending) {
            ServiceJobDTO request = baseJob(booking, ServiceMode.INDOOR);
            request.setItemName(item.getItemName());
            request.setDeviceType(item.getDeviceType());
            request.setSerialNo(item.getSerialNo());
            request.setColor(item.getColor());
            request.setAccessories(item.getAccessories());
            request.setProblemDesc(firstNonBlank(item.getProblemDesc(), booking.getComplaintNote()));
            request.setItemCondition(item.getItemCondition());
            ServiceJobDTO created = serviceJobService.create(request);
            item.setConvertedJobId(created.getId());
        }
        itemRepository.saveAll(pending);
        BookingDTO result = toDto(booking, true);
        broadcast("BOOKING_INDOOR_CONVERTED");
        return result;
    }

    @Transactional
    public void delete(Integer id) {
        Booking booking = requireForUpdate(id);
        if (booking.getStatus() != BookingStatus.CONFIRMED)
            throw new IllegalStateException("Only CONFIRMED booking can be deleted");
        if (!booking.getItems().isEmpty())
            throw new IllegalStateException("Booking with received items cannot be deleted");
        if (!serviceJobRepository.findAllByBookingIdOrderByIdAsc(id).isEmpty())
            throw new IllegalStateException("Booking with linked service jobs cannot be deleted");
        repository.delete(booking);
        broadcast("BOOKING_DELETED");
    }

    private ServiceJobDTO baseJob(Booking booking, ServiceMode mode) {
        ServiceJobDTO dto = new ServiceJobDTO();
        dto.setCustomerId(booking.getCustomer().getId());
        dto.setBookingId(booking.getId());
        dto.setServiceMode(mode);
        dto.setRemark(booking.getRemark());
        return dto;
    }

    private Booking require(Integer id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Booking not found: " + id));
    }

    private Booking requireForUpdate(Integer id) {
        return repository.findByIdForUpdate(id)
                .orElseThrow(() -> new ResourceNotFoundException("Booking not found: " + id));
    }

    private BookingDTO toDto(Booking booking, boolean detail) {
        BookingDTO dto = new BookingDTO();
        dto.setId(booking.getId());
        dto.setBookingNo(booking.getBookingNo());
        dto.setCustomerId(booking.getCustomer().getId());
        dto.setCustomerName(booking.getCustomer().getName());
        dto.setCustomerPhone(booking.getCustomer().getPhone());
        dto.setBookingDate(booking.getBookingDate());
        dto.setAppointmentDate(booking.getAppointmentDate());
        dto.setComplaintNote(booking.getComplaintNote());
        dto.setStatus(booking.getStatus());
        dto.setRemark(booking.getRemark());
        dto.setCreatedAt(booking.getCreatedAt());
        dto.setUpdatedAt(booking.getUpdatedAt());
        if (detail) {
            dto.setItems(booking.getItems().stream().map(this::toItemDto).toList());
            dto.setLinkedJobs(serviceJobService.findByBookingId(booking.getId()));
        }
        dto.setUnconvertedItemCount(booking.getItems().stream()
                .filter(item -> item.getConvertedJobId() == null).count());
        dto.setFullyConverted(isFullyConverted(booking));
        return dto;
    }

    private BookingItemDTO toItemDto(BookingItem item) {
        BookingItemDTO dto = new BookingItemDTO();
        dto.setId(item.getId());
        dto.setItemName(item.getItemName());
        dto.setDeviceType(item.getDeviceType());
        dto.setSerialNo(item.getSerialNo());
        dto.setColor(item.getColor());
        dto.setAccessories(item.getAccessories());
        dto.setProblemDesc(item.getProblemDesc());
        dto.setItemCondition(item.getItemCondition());
        dto.setNoticed(item.getNoticed());
        dto.setConvertedJobId(item.getConvertedJobId());
        if (item.getPhotos() != null) {
            dto.setPhotos(item.getPhotos().stream().map(this::toPhotoDto).toList());
        }
        return dto;
    }

    private BookingItemPhotoDTO toPhotoDto(BookingItemPhoto photo) {
        BookingItemPhotoDTO dto = new BookingItemPhotoDTO();
        dto.setId(photo.getId());
        dto.setSlot(photo.getSlot());
        dto.setFileName(photo.getFileName());
        dto.setContentType(photo.getContentType());
        dto.setDataUrl(photo.getDataUrl());
        dto.setImagePath(photo.getImagePath());
        dto.setThumbnailPath(photo.getThumbnailPath());
        dto.setUploadedAt(photo.getUploadedAt());
        return dto;
    }

    private void migrateLegacyPhotos(Booking booking) {
        booking.getItems().forEach(item -> item.getPhotos().forEach(photo -> {
            if (photo.getDataUrl() == null || photo.getDataUrl().isBlank()
                    || (photo.getImagePath() != null && photo.getThumbnailPath() != null)) return;
            BookingPhotoStorageService.StoredPhoto stored = bookingPhotoStorageService.store(
                    photo.getDataUrl(), booking.getId(), photo.getSlot());
            photo.setImagePath(stored.imagePath());
            photo.setThumbnailPath(stored.thumbnailPath());
            photo.setContentType("image/webp");
            photo.setDataUrl(null);
        }));
    }

    private void attachPhotos(BookingItem item, List<BookingItemPhotoDTO> photos) {
        if (photos == null || photos.isEmpty()) return;
        if (photos.size() > MAX_PHOTOS_PER_ITEM)
            throw new IllegalArgumentException("Each device can have at most " + MAX_PHOTOS_PER_ITEM + " photos");
        Set<Integer> usedSlots = new HashSet<>();
        int autoSlot = 1;
        for (BookingItemPhotoDTO photoDto : photos) {
            String dataUrl = trimToNull(photoDto.getDataUrl());
            if (dataUrl == null) continue;
            if (dataUrl.length() > MAX_PHOTO_DATA_URL_LENGTH)
                throw new IllegalArgumentException("Device photo is too large");
            int slot = photoDto.getSlot() != null ? photoDto.getSlot() : autoSlot;
            if (slot < 1 || slot > MAX_PHOTOS_PER_ITEM)
                throw new IllegalArgumentException("Photo slot must be between 1 and " + MAX_PHOTOS_PER_ITEM);
            if (!usedSlots.add(slot))
                throw new IllegalArgumentException("Duplicate photo slot: " + slot);
                BookingPhotoStorageService.StoredPhoto stored = bookingPhotoStorageService.store(dataUrl, item.getBooking().getId(), slot);
                item.getPhotos().add(BookingItemPhoto.builder()
                    .bookingItem(item)
                    .slot(slot)
                    .fileName(trimToNull(photoDto.getFileName()))
                    .contentType("image/webp")
                    .dataUrl(null)
                    .imagePath(stored.imagePath())
                    .thumbnailPath(stored.thumbnailPath())
                    .build());
            autoSlot++;
        }
        if (item.getPhotos().size() > MAX_PHOTOS_PER_ITEM)
            throw new IllegalArgumentException("Each device can have at most " + MAX_PHOTOS_PER_ITEM + " photos");
    }

    private boolean isFullyConverted(Booking booking) {
        if (booking.getStatus() == BookingStatus.CONFIRMED)
            return serviceJobRepository.existsByBookingIdAndServiceMode(booking.getId(), ServiceMode.OUTDOOR);
        if (booking.getStatus() == BookingStatus.ARRIVED)
            return !booking.getItems().isEmpty()
                    && booking.getItems().stream().allMatch(item -> item.getConvertedJobId() != null);
        return false;
    }

    private String generateBookingNo(Integer id) {
        var settings = companySettingsRepository.findAll().stream().findFirst().orElse(null);
        String prefix = settings != null ? trimToNull(settings.getBookingPrefix()) : null;
        if (prefix == null) prefix = "BK";
        prefix = prefix.replaceAll("-+$", "").toUpperCase(Locale.ROOT);
        int digits = settings != null && settings.getBookingDigits() != null ? settings.getBookingDigits() : 6;
        digits = Math.max(1, Math.min(digits, 12));
        String number = String.format("%0" + digits + "d", id);
        int maxPrefixLength = Math.max(1, 20 - number.length() - 1);
        if (prefix.length() > maxPrefixLength) prefix = prefix.substring(0, maxPrefixLength);
        return prefix + "-" + number;
    }

    private String outdoorItemName(Booking booking) {
        String value = firstNonBlank(booking.getComplaintNote(),
                "Outdoor service - " + booking.getCustomer().getName());
        return value.length() <= 200 ? value : value.substring(0, 200);
    }

    private void validateBase(BookingDTO dto) {
        if (dto == null || dto.getCustomerId() == null)
            throw new IllegalArgumentException("Customer is required");
    }

    private static LocalDate parseDate(String value) {
        return value == null || value.isBlank() ? null : LocalDate.parse(value);
    }

    private static String trimToNull(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static String firstNonBlank(String first, String fallback) {
        String value = trimToNull(first);
        return value != null ? value : trimToNull(fallback);
    }

    private void broadcast(String event) {
        messagingTemplate.convertAndSend("/topic/booking", event);
    }
}
