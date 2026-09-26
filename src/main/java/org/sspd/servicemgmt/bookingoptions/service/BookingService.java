package org.sspd.servicemgmt.bookingoptions.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.sspd.servicemgmt.bookingoptions.dto.BookingDTO;
import org.sspd.servicemgmt.bookingoptions.dto.BookingFilterSummaryDTO;
import org.sspd.servicemgmt.bookingoptions.dto.BookingItemDTO;
import org.sspd.servicemgmt.bookingoptions.dto.BookingItemComponentDTO;
import org.sspd.servicemgmt.bookingoptions.dto.BookingItemPhotoDTO;
import org.sspd.servicemgmt.bookingoptions.dto.BookingRequestPhotoDTO;
import org.sspd.servicemgmt.bookingoptions.model.Booking;
import org.sspd.servicemgmt.bookingoptions.model.BookingItem;
import org.sspd.servicemgmt.bookingoptions.model.BookingItemComponent;
import org.sspd.servicemgmt.bookingoptions.model.BookingItemPhoto;
import org.sspd.servicemgmt.bookingoptions.model.BookingRequestPhoto;
import org.sspd.servicemgmt.bookingoptions.model.BookingStatus;
import org.sspd.servicemgmt.bookingoptions.repository.BookingItemRepository;
import org.sspd.servicemgmt.bookingoptions.repository.BookingItemSummaryProjection;
import org.sspd.servicemgmt.bookingoptions.repository.BookingRepository;
import org.sspd.servicemgmt.bookingoptions.repository.BookingRequestPhotoRepository;
import org.sspd.servicemgmt.companysettingoptions.repository.CompanySettingsRepository;
import org.sspd.servicemgmt.customeroptions.repository.CustomerRepository;
import org.sspd.servicemgmt.exceptionhandler.ResourceNotFoundException;
import org.sspd.servicemgmt.servicejoboptions.dto.ServiceJobDTO;
import org.sspd.servicemgmt.servicejoboptions.model.ServiceMode;
import org.sspd.servicemgmt.servicejoboptions.repository.ServiceJobRepository;
import org.sspd.servicemgmt.dataevent.DataEventPublisher;
import org.sspd.servicemgmt.servicebookingsettingsoptions.service.ServiceBookingSettingsService;
import org.sspd.servicemgmt.servicejoboptions.service.ServiceJobService;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class BookingService {
    private static final int MAX_REQUEST_PHOTOS = 3;
    private static final int MAX_COMPONENTS_PER_ITEM = 30;
    private static final int MAX_PHOTO_DATA_URL_LENGTH = 4_500_000;

    private final BookingRepository repository;
    private final BookingItemRepository itemRepository;
    private final BookingRequestPhotoRepository requestPhotoRepository;
    private final CustomerRepository customerRepository;
    private final CompanySettingsRepository companySettingsRepository;
    private final ServiceJobRepository serviceJobRepository;
    private final ServiceJobService serviceJobService;
    private final DataEventPublisher dataEventPublisher;
    private final BookingPhotoStorageService bookingPhotoStorageService;
    private final ServiceBookingSettingsService serviceBookingSettingsService;

    @Transactional(readOnly = true)
    public Page<BookingDTO> findAll(
            String search,
            String dateFrom,
            String dateTo,
            BookingStatus status,
            Integer customerId,
            String source,
            int page,
            int size) {
        Page<Booking> bookings = repository.search(
                normalizeSearch(search),
                parseDate(dateFrom),
                parseDate(dateTo),
                status,
                customerId,
                normalizeSource(source),
                PageRequest.of(Math.max(0, page), Math.max(1, Math.min(size, 500)),
                        Sort.by(Sort.Direction.DESC, "id")));
        // Admin list needs conversion flags, not request photo payloads.
        List<BookingDTO> mapped = mapSummaries(bookings.getContent(), false);
        return new org.springframework.data.domain.PageImpl<>(mapped, bookings.getPageable(), bookings.getTotalElements());
    }

    @Transactional(readOnly = true)
    public BookingFilterSummaryDTO filterSummary(
            String search,
            String dateFrom,
            String dateTo,
            BookingStatus status,
            Integer customerId,
            String source) {
        String q = normalizeSearch(search);
        LocalDate from = parseDate(dateFrom);
        LocalDate to = parseDate(dateTo);
        String sourceFilter = normalizeSource(source);

        long total = repository.countFiltered(q, from, to, status, customerId, sourceFilter);
        long appCount = repository.countFiltered(q, from, to, status, customerId, "CUSTOMER_APP");

        Map<String, Long> byStatus = new LinkedHashMap<>();
        for (BookingStatus s : BookingStatus.values()) {
            byStatus.put(s.name(), 0L);
        }
        for (Object[] row : repository.countGroupedByStatus(q, from, to, customerId, sourceFilter)) {
            if (row == null || row.length < 2 || row[0] == null) continue;
            BookingStatus s = (BookingStatus) row[0];
            long count = row[1] instanceof Number n ? n.longValue() : 0L;
            byStatus.put(s.name(), count);
        }

        return BookingFilterSummaryDTO.builder()
                .total(total)
                .appCount(appCount)
                .byStatus(byStatus)
                .build();
    }

    private static String normalizeSearch(String search) {
        return search == null ? "" : search.trim();
    }

    private static String normalizeSource(String source) {
        if (source == null || source.isBlank()) return null;
        String trimmed = source.trim();
        if ("APP".equalsIgnoreCase(trimmed) || "CUSTOMER_APP".equalsIgnoreCase(trimmed)) {
            return "CUSTOMER_APP";
        }
        return trimmed;
    }

    @Transactional(readOnly = true)
    public List<BookingDTO> findSummariesByCustomerId(Integer customerId) {
        return mapSummaries(repository.findByCustomer_IdOrderByIdDesc(customerId), true);
    }

    /**
     * Single-booking summary helper. Prefer {@link #findAll} / {@link #findSummariesByCustomerId}
     * for list endpoints so stats/photos are batch-loaded.
     */
    public BookingDTO toSummaryDto(Booking booking) {
        return mapSummaries(List.of(booking), true).get(0);
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
                .source(trimToNull(dto.getSource()))
                .requestedServiceName(trimToNull(dto.getRequestedServiceName()))
                .requestedServiceId(dto.getRequestedServiceId())
                .servicePriceSnapshot(dto.getServicePriceSnapshot())
                .servicePriceType(trimToNull(dto.getServicePriceType()))
                .estimateApprovalStatus(trimToNull(dto.getEstimateApprovalStatus()))
                .requestType(trimToNull(dto.getRequestType()))
                .deviceCategory(trimToNull(dto.getDeviceCategory()))
                .deviceName(trimToNull(dto.getDeviceName()))
                .requestedServiceMode(trimToNull(dto.getRequestedServiceMode()))
                .serviceAddress(trimToNull(dto.getServiceAddress()))
                .urgency(trimToNull(dto.getUrgency()))
                .contactPreference(trimToNull(dto.getContactPreference()))
                .serviceDate(dto.getServiceDate())
                .arrivalWindowId(dto.getArrivalWindowId())
                .preferredTime(dto.getPreferredTime())
                .preferredAnytime(dto.getPreferredAnytime() == null || dto.getPreferredAnytime())
                .customerPreferenceNote(trimToNull(dto.getCustomerPreferenceNote()))
                .requestPhotos(new ArrayList<>())
                .items(new ArrayList<>())
                .build();
        booking = repository.saveAndFlush(booking);
        booking.setBookingNo(generateBookingNo(booking.getId()));
        attachRequestPhotos(booking, dto.getRequestPhotos());
        BookingDTO result = toDto(repository.save(booking), true);
        broadcast("BOOKING_CREATED");
        return result;
    }

    @Transactional
    public BookingDTO update(Integer id, BookingDTO dto) {
        Booking booking = requireForUpdate(id);
        if (isClosed(booking))
            throw new IllegalStateException("Canceled or rejected booking cannot be edited");
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
        if (booking.getStatus() == BookingStatus.REJECTED)
            throw new IllegalStateException("Rejected booking cannot be canceled");
        if (!serviceJobRepository.findAllByBookingIdOrderByIdAsc(id).isEmpty())
            throw new IllegalStateException("Booking with linked service jobs cannot be canceled");
        booking.setStatus(BookingStatus.CANCELED);
        BookingDTO result = toDto(repository.save(booking), true);
        broadcast("BOOKING_CANCELED");
        return result;
    }

    @Transactional
    public BookingDTO reject(Integer id, String reason, String rejectedBy) {
        Booking booking = requireForUpdate(id);
        if (booking.getStatus() == BookingStatus.REJECTED) return toDto(booking, true);
        if (booking.getStatus() != BookingStatus.CONFIRMED)
            throw new IllegalStateException("Only confirmed booking can be rejected");
        if (!serviceJobRepository.findAllByBookingIdOrderByIdAsc(id).isEmpty())
            throw new IllegalStateException("Booking with linked service jobs cannot be rejected");
        String cleanReason = trimToNull(reason);
        if (cleanReason == null)
            throw new IllegalArgumentException("Rejection reason is required");
        if (cleanReason.length() > 2000)
            throw new IllegalArgumentException("Rejection reason must not exceed 2000 characters");
        booking.setStatus(BookingStatus.REJECTED);
        booking.setRejectionReason(cleanReason);
        booking.setRejectedAt(java.time.LocalDateTime.now());
        booking.setRejectedBy(trimToNull(rejectedBy));
        BookingDTO result = toDto(repository.save(booking), true);
        broadcast("BOOKING_REJECTED");
        return result;
    }

    @Transactional
    public BookingDTO addItems(Integer id, List<BookingItemDTO> itemDtos) {
        Booking booking = requireForUpdate(id);
        if (isClosed(booking))
            throw new IllegalStateException("Canceled or rejected booking cannot receive items");
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
                    .components(new ArrayList<>())
                    .build();
            attachPhotos(item, dto.getPhotos());
            attachComponents(item, dto.getComponents());
            booking.getItems().add(item);
        }
        booking.setStatus(BookingStatus.ARRIVED);
        BookingDTO result = toDto(repository.save(booking), true);
        broadcast("BOOKING_ITEMS_RECEIVED");
        return result;
    }

    @Transactional
    public BookingDTO updateItem(Integer bookingId, Integer itemId, BookingItemDTO dto) {
        Booking booking = requireForUpdate(bookingId);
        if (isClosed(booking))
            throw new IllegalStateException("Canceled or rejected booking cannot be edited");
        BookingItem item = itemRepository.findByIdAndBookingId(itemId, bookingId)
                .orElseThrow(() -> new ResourceNotFoundException("Booking item not found"));

        boolean converted = item.getConvertedJobId() != null;
        boolean photosProvided = dto.getPhotos() != null;
        if (converted && photosProvided) {
            throw new IllegalStateException("Converted booking item photos cannot be changed");
        }

        String itemName = trimToNull(dto.getItemName());
        if (itemName == null) throw new IllegalArgumentException("Item name is required");
        item.setItemName(itemName);
        item.setDeviceType(trimToNull(dto.getDeviceType()));
        item.setSerialNo(trimToNull(dto.getSerialNo()));
        item.setColor(trimToNull(dto.getColor()));
        item.setAccessories(trimToNull(dto.getAccessories()));
        item.setProblemDesc(firstNonBlank(dto.getProblemDesc(), booking.getComplaintNote()));
        item.setItemCondition(trimToNull(dto.getItemCondition()));
        item.setNoticed(trimToNull(dto.getNoticed()));
        if (dto.getComponents() != null) {
            syncComponents(item, dto.getComponents());
        }

        if (photosProvided) {
            syncItemPhotos(item, dto.getPhotos());
        }

        itemRepository.save(item);
        BookingDTO result = toDto(booking, true);
        broadcast("BOOKING_ITEM_UPDATED");
        return result;
    }

    @Transactional
    public BookingDTO removeItem(Integer bookingId, Integer itemId) {
        Booking booking = requireForUpdate(bookingId);
        if (isClosed(booking))
            throw new IllegalStateException("Canceled or rejected booking cannot be edited");
        BookingItem item = itemRepository.findByIdAndBookingId(itemId, bookingId)
                .orElseThrow(() -> new ResourceNotFoundException("Booking item not found"));
        if (item.getConvertedJobId() != null)
            throw new IllegalStateException("Converted booking item cannot be removed");
        List<String[]> filesToDelete = item.getPhotos().stream()
                .map(photo -> new String[]{photo.getImagePath(), photo.getThumbnailPath()})
                .toList();
        booking.getItems().removeIf(existing -> existing.getId().equals(itemId));
        itemRepository.delete(item);
        if (booking.getItems().isEmpty()) booking.setStatus(BookingStatus.CONFIRMED);
        BookingDTO result = toDto(repository.save(booking), true);
        schedulePhotoFileCleanup(filesToDelete, List.of());
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
        request.setProblemDesc(firstNonBlank(booking.getComplaintNote(), booking.getRequestedServiceName()));
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
        List<String[]> requestFiles = booking.getRequestPhotos().stream()
                .map(photo -> new String[]{photo.getImagePath(), photo.getThumbnailPath()})
                .toList();
        repository.delete(booking);
        schedulePhotoFileCleanup(requestFiles, List.of());
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

    private List<BookingDTO> mapSummaries(List<Booking> bookings, boolean includeRequestPhotos) {
        List<Integer> ids = bookings.stream().map(Booking::getId).filter(Objects::nonNull).toList();
        Map<Integer, BookingSummaryStats> statsById = loadSummaryStats(ids);
        Map<Integer, List<BookingRequestPhotoDTO>> photosById = includeRequestPhotos
                ? loadRequestPhotosByBookingIds(ids)
                : Map.of();
        return bookings.stream()
                .map(booking -> toSummaryDto(
                        booking,
                        statsById.getOrDefault(booking.getId(), BookingSummaryStats.EMPTY),
                        photosById.getOrDefault(booking.getId(), List.of())))
                .toList();
    }

    private BookingDTO toSummaryDto(
            Booking booking,
            BookingSummaryStats stats,
            List<BookingRequestPhotoDTO> requestPhotos) {
        BookingDTO dto = mapBaseFields(booking);
        dto.setRequestPhotos(requestPhotos != null ? requestPhotos : List.of());
        dto.setItems(List.of());
        dto.setLinkedJobs(List.of());
        dto.setUnconvertedItemCount(stats.unconvertedCount());
        dto.setFullyConverted(stats.fullyConverted(booking.getStatus()));
        return dto;
    }

    private Map<Integer, BookingSummaryStats> loadSummaryStats(Collection<Integer> bookingIds) {
        if (bookingIds == null || bookingIds.isEmpty()) {
            return Map.of();
        }
        Map<Integer, BookingSummaryStats> stats = new HashMap<>();
        for (BookingItemSummaryProjection row : itemRepository.summarizeByBookingIds(bookingIds)) {
            stats.put(row.getBookingId(), new BookingSummaryStats(
                    row.getItemCount(),
                    row.getUnconvertedCount(),
                    false));
        }
        Set<Integer> outdoorIds = new HashSet<>(
                serviceJobRepository.findBookingIdsByServiceMode(bookingIds, ServiceMode.OUTDOOR));
        for (Integer id : bookingIds) {
            BookingSummaryStats existing = stats.getOrDefault(id, BookingSummaryStats.EMPTY);
            stats.put(id, new BookingSummaryStats(
                    existing.itemCount(),
                    existing.unconvertedCount(),
                    outdoorIds.contains(id)));
        }
        return stats;
    }

    private Map<Integer, List<BookingRequestPhotoDTO>> loadRequestPhotosByBookingIds(Collection<Integer> bookingIds) {
        if (bookingIds == null || bookingIds.isEmpty()) {
            return Map.of();
        }
        Map<Integer, List<BookingRequestPhotoDTO>> byBooking = new HashMap<>();
        for (BookingRequestPhoto photo : requestPhotoRepository.findAllByBookingIdIn(bookingIds)) {
            byBooking.computeIfAbsent(photo.getBooking().getId(), ignored -> new ArrayList<>())
                    .add(toRequestPhotoDto(photo));
        }
        return byBooking;
    }

    private BookingDTO mapBaseFields(Booking booking) {
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
        dto.setRejectionReason(booking.getRejectionReason());
        dto.setRejectedAt(booking.getRejectedAt());
        dto.setRejectedBy(booking.getRejectedBy());
        dto.setSource(booking.getSource());
        dto.setRequestedServiceName(booking.getRequestedServiceName());
        dto.setRequestedServiceId(booking.getRequestedServiceId());
        dto.setServiceNameSnapshot(booking.getRequestedServiceName());
        dto.setServicePriceSnapshot(booking.getServicePriceSnapshot());
        dto.setServicePriceType(booking.getServicePriceType());
        dto.setEstimateApprovalStatus(booking.getEstimateApprovalStatus());
        dto.setRequestType(booking.getRequestType());
        dto.setDeviceCategory(booking.getDeviceCategory());
        dto.setDeviceName(booking.getDeviceName());
        dto.setRequestedServiceMode(booking.getRequestedServiceMode());
        dto.setServiceAddress(booking.getServiceAddress());
        dto.setUrgency(booking.getUrgency());
        dto.setContactPreference(booking.getContactPreference());
        dto.setServiceDate(booking.getServiceDate());
        dto.setArrivalWindowId(booking.getArrivalWindowId());
        dto.setPreferredTime(booking.getPreferredTime());
        dto.setPreferredAnytime(booking.getPreferredAnytime() == null || booking.getPreferredAnytime());
        dto.setCustomerPreferenceNote(booking.getCustomerPreferenceNote());
        dto.setCreatedAt(booking.getCreatedAt());
        dto.setUpdatedAt(booking.getUpdatedAt());
        return dto;
    }

    private BookingDTO toDto(Booking booking, boolean detail) {
        BookingDTO dto = mapBaseFields(booking);
        if (detail) {
            if (booking.getRequestPhotos() != null) {
                dto.setRequestPhotos(booking.getRequestPhotos().stream()
                        .map(this::toRequestPhotoDto)
                        .toList());
            }
            dto.setItems(booking.getItems().stream().map(this::toItemDto).toList());
            dto.setLinkedJobs(serviceJobService.findByBookingId(booking.getId()));
            dto.setUnconvertedItemCount(booking.getItems().stream()
                    .filter(item -> item.getConvertedJobId() == null).count());
            dto.setFullyConverted(isFullyConverted(booking));
        } else {
            // Summary path without preloaded stats — keep behavior for accidental callers,
            // but list endpoints must use mapSummaries() to avoid N+1.
            BookingSummaryStats stats = loadSummaryStats(List.of(booking.getId()))
                    .getOrDefault(booking.getId(), BookingSummaryStats.EMPTY);
            dto.setRequestPhotos(List.of());
            dto.setUnconvertedItemCount(stats.unconvertedCount());
            dto.setFullyConverted(stats.fullyConverted(booking.getStatus()));
        }
        return dto;
    }

    private record BookingSummaryStats(long itemCount, long unconvertedCount, boolean hasOutdoorJob) {
        static final BookingSummaryStats EMPTY = new BookingSummaryStats(0, 0, false);

        boolean fullyConverted(BookingStatus status) {
            if (status == BookingStatus.CONFIRMED) {
                return hasOutdoorJob;
            }
            if (status == BookingStatus.ARRIVED) {
                return itemCount > 0 && unconvertedCount == 0;
            }
            return false;
        }
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
        if (item.getComponents() != null) {
            dto.setComponents(item.getComponents().stream().map(this::toComponentDto).toList());
        }
        return dto;
    }

    private BookingItemComponentDTO toComponentDto(BookingItemComponent component) {
        BookingItemComponentDTO dto = new BookingItemComponentDTO();
        dto.setId(component.getId());
        dto.setComponentType(component.getComponentType());
        dto.setBrand(component.getBrand());
        dto.setModel(component.getModel());
        dto.setSpecification(component.getSpecification());
        dto.setSerialNo(component.getSerialNo());
        dto.setQuantity(component.getQuantity());
        dto.setConditionNote(component.getConditionNote());
        return dto;
    }

    private void syncComponents(BookingItem item, List<BookingItemComponentDTO> components) {
        item.getComponents().clear();
        attachComponents(item, components);
    }

    private void attachComponents(BookingItem item, List<BookingItemComponentDTO> components) {
        if (components == null || components.isEmpty()) return;
        if (components.size() > MAX_COMPONENTS_PER_ITEM) {
            throw new IllegalArgumentException("Each device can have at most " + MAX_COMPONENTS_PER_ITEM + " components");
        }
        for (BookingItemComponentDTO componentDto : components) {
            boolean empty = trimToNull(componentDto.getComponentType()) == null
                    && trimToNull(componentDto.getBrand()) == null
                    && trimToNull(componentDto.getModel()) == null
                    && trimToNull(componentDto.getSpecification()) == null
                    && trimToNull(componentDto.getSerialNo()) == null
                    && trimToNull(componentDto.getConditionNote()) == null;
            if (empty) continue;
            String type = limited(componentDto.getComponentType(), 40, "Component type");
            if (type == null) throw new IllegalArgumentException("Component type is required");
            int quantity = componentDto.getQuantity() == null ? 1 : componentDto.getQuantity();
            if (quantity < 1 || quantity > 100) {
                throw new IllegalArgumentException("Component quantity must be between 1 and 100");
            }
            item.getComponents().add(BookingItemComponent.builder()
                    .bookingItem(item)
                    .componentType(type.toUpperCase(Locale.ROOT).replace(' ', '_'))
                    .brand(limited(componentDto.getBrand(), 120, "Component brand"))
                    .model(limited(componentDto.getModel(), 160, "Component model"))
                    .specification(limited(componentDto.getSpecification(), 255, "Component specification"))
                    .serialNo(limited(componentDto.getSerialNo(), 160, "Component serial"))
                    .quantity(quantity)
                    .conditionNote(trimToNull(componentDto.getConditionNote()))
                    .build());
        }
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

    private BookingRequestPhotoDTO toRequestPhotoDto(BookingRequestPhoto photo) {
        BookingRequestPhotoDTO dto = new BookingRequestPhotoDTO();
        dto.setId(photo.getId());
        dto.setSlot(photo.getSlot());
        dto.setFileName(photo.getFileName());
        dto.setContentType(photo.getContentType());
        dto.setImagePath(photo.getImagePath());
        dto.setThumbnailPath(photo.getThumbnailPath());
        dto.setUploadedAt(photo.getUploadedAt());
        return dto;
    }

    private void attachRequestPhotos(Booking booking, List<BookingRequestPhotoDTO> photos) {
        if (photos == null || photos.isEmpty()) return;
        if (photos.size() > MAX_REQUEST_PHOTOS) {
            throw new IllegalArgumentException("A booking request can have at most " + MAX_REQUEST_PHOTOS + " photos");
        }
        Set<Integer> usedSlots = new HashSet<>();
        List<String[]> deleteOnRollback = new ArrayList<>();
        try {
            int autoSlot = 1;
            for (BookingRequestPhotoDTO photoDto : photos) {
                String dataUrl = trimToNull(photoDto.getDataUrl());
                if (dataUrl == null) continue;
                if (dataUrl.length() > MAX_PHOTO_DATA_URL_LENGTH) {
                    throw new IllegalArgumentException("Booking request photo is too large");
                }
                int slot = photoDto.getSlot() != null ? photoDto.getSlot() : autoSlot;
                if (slot < 1 || slot > MAX_REQUEST_PHOTOS) {
                    throw new IllegalArgumentException("Photo slot must be between 1 and " + MAX_REQUEST_PHOTOS);
                }
                if (!usedSlots.add(slot)) {
                    throw new IllegalArgumentException("Duplicate photo slot: " + slot);
                }
                BookingPhotoStorageService.StoredPhoto stored =
                        bookingPhotoStorageService.store(dataUrl, booking.getId(), slot);
                deleteOnRollback.add(new String[]{stored.imagePath(), stored.thumbnailPath()});
                booking.getRequestPhotos().add(BookingRequestPhoto.builder()
                        .booking(booking)
                        .slot(slot)
                        .fileName(trimToNull(photoDto.getFileName()))
                        .contentType("image/webp")
                        .imagePath(stored.imagePath())
                        .thumbnailPath(stored.thumbnailPath())
                        .build());
                autoSlot++;
            }
        } finally {
            schedulePhotoFileCleanup(List.of(), deleteOnRollback);
        }
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
        int maxPhotos = maxPhotosPerItem();
        if (photos.size() > maxPhotos)
            throw new IllegalArgumentException("Each device can have at most " + maxPhotos + " photos");
        Set<Integer> usedSlots = new HashSet<>();
        int autoSlot = 1;
        for (BookingItemPhotoDTO photoDto : photos) {
            String dataUrl = trimToNull(photoDto.getDataUrl());
            if (dataUrl == null) continue;
            if (dataUrl.length() > MAX_PHOTO_DATA_URL_LENGTH)
                throw new IllegalArgumentException("Device photo is too large");
            int slot = photoDto.getSlot() != null ? photoDto.getSlot() : autoSlot;
            if (slot < 1 || slot > maxPhotos)
                throw new IllegalArgumentException("Photo slot must be between 1 and " + maxPhotos);
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
        if (item.getPhotos().size() > maxPhotos)
            throw new IllegalArgumentException("Each device can have at most " + maxPhotos + " photos");
    }

    /**
     * Product-style photo sync for configurable numbered slots:
     * - slots present in payload are kept
     * - {@code dataUrl} replaces/adds that slot
     * - path-only entries keep an existing slot (no client-supplied path hijack)
     * - slots omitted from payload are deleted
     * Disk deletes run only after successful commit; newly stored files are removed on rollback.
     */
    private void syncItemPhotos(BookingItem item, List<BookingItemPhotoDTO> photos) {
        List<BookingItemPhotoDTO> incoming = photos == null ? List.of() : photos;
        int maxPhotos = maxPhotosPerItem();
        Set<Integer> existingSlots = item.getPhotos().stream()
                .map(BookingItemPhoto::getSlot)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        Set<Integer> keepSlots = new HashSet<>();
        for (BookingItemPhotoDTO photoDto : incoming) {
            Integer slot = photoDto.getSlot();
            if (slot == null || slot < 1)
                throw new IllegalArgumentException("Photo slot must be between 1 and " + maxPhotos);
            // Grandfather slots already stored above a newly lowered limit.
            if (slot > maxPhotos && !existingSlots.contains(slot))
                throw new IllegalArgumentException("Photo slot must be between 1 and " + maxPhotos);
            if (!keepSlots.add(slot))
                throw new IllegalArgumentException("Duplicate photo slot: " + slot);
        }
        long withinCap = incoming.stream().filter(dto -> dto.getSlot() != null && dto.getSlot() <= maxPhotos).count();
        if (withinCap > maxPhotos)
            throw new IllegalArgumentException("Each device can have at most " + maxPhotos + " photos");

        List<String[]> deleteOnCommit = new ArrayList<>();
        List<String[]> deleteOnRollback = new ArrayList<>();

        List<BookingItemPhoto> removed = item.getPhotos().stream()
                .filter(photo -> photo.getSlot() != null && !keepSlots.contains(photo.getSlot()))
                .toList();
        for (BookingItemPhoto photo : removed) {
            deleteOnCommit.add(new String[]{photo.getImagePath(), photo.getThumbnailPath()});
            item.getPhotos().remove(photo);
        }

        Integer bookingId = item.getBooking() != null ? item.getBooking().getId() : null;
        for (BookingItemPhotoDTO photoDto : incoming) {
            int slot = photoDto.getSlot();
            String dataUrl = trimToNull(photoDto.getDataUrl());
            BookingItemPhoto existing = item.getPhotos().stream()
                    .filter(photo -> Objects.equals(photo.getSlot(), slot))
                    .findFirst()
                    .orElse(null);

            if (dataUrl != null) {
                if (dataUrl.length() > MAX_PHOTO_DATA_URL_LENGTH)
                    throw new IllegalArgumentException("Device photo is too large");
                BookingPhotoStorageService.StoredPhoto stored =
                        bookingPhotoStorageService.store(dataUrl, bookingId, slot);
                deleteOnRollback.add(new String[]{stored.imagePath(), stored.thumbnailPath()});
                if (existing != null) {
                    deleteOnCommit.add(new String[]{existing.getImagePath(), existing.getThumbnailPath()});
                    existing.setFileName(trimToNull(photoDto.getFileName()));
                    existing.setContentType("image/webp");
                    existing.setDataUrl(null);
                    existing.setImagePath(stored.imagePath());
                    existing.setThumbnailPath(stored.thumbnailPath());
                } else {
                    item.getPhotos().add(BookingItemPhoto.builder()
                            .bookingItem(item)
                            .slot(slot)
                            .fileName(trimToNull(photoDto.getFileName()))
                            .contentType("image/webp")
                            .dataUrl(null)
                            .imagePath(stored.imagePath())
                            .thumbnailPath(stored.thumbnailPath())
                            .build());
                }
                continue;
            }

            if (existing != null) {
                // Keep existing stored file; ignore client-supplied paths (prevents cross-booking hijack).
                if (photoDto.getFileName() != null) existing.setFileName(trimToNull(photoDto.getFileName()));
                continue;
            }

            // No existing slot and no dataUrl — do not accept arbitrary client paths.
            throw new IllegalArgumentException(
                    "Photo slot " + slot + " requires a dataUrl to add a new image");
        }

        schedulePhotoFileCleanup(deleteOnCommit, deleteOnRollback);
    }

    private int maxPhotosPerItem() {
        return serviceBookingSettingsService.getMaxPhotosPerItem();
    }

    /**
     * Deletes obsolete files after commit; deletes newly written files if the transaction rolls back.
     * When no transaction is active (unit tests), commit deletions run immediately.
     */
    private void schedulePhotoFileCleanup(List<String[]> deleteOnCommit, List<String[]> deleteOnRollback) {
        List<String[]> onCommit = deleteOnCommit == null ? List.of() : List.copyOf(deleteOnCommit);
        List<String[]> onRollback = deleteOnRollback == null ? List.of() : List.copyOf(deleteOnRollback);
        if (onCommit.isEmpty() && onRollback.isEmpty()) return;

        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            onCommit.forEach(paths -> bookingPhotoStorageService.deleteExisting(paths[0], paths[1]));
            return;
        }

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status == STATUS_COMMITTED) {
                    onCommit.forEach(paths -> bookingPhotoStorageService.deleteExisting(paths[0], paths[1]));
                } else {
                    onRollback.forEach(paths -> bookingPhotoStorageService.deleteExisting(paths[0], paths[1]));
                }
            }
        });
    }

    private boolean isFullyConverted(Booking booking) {
        if (booking.getStatus() == BookingStatus.CONFIRMED)
            return serviceJobRepository.existsByBookingIdAndServiceMode(booking.getId(), ServiceMode.OUTDOOR);
        if (booking.getStatus() == BookingStatus.ARRIVED)
            return !booking.getItems().isEmpty()
                    && booking.getItems().stream().allMatch(item -> item.getConvertedJobId() != null);
        return false;
    }

    private boolean isClosed(Booking booking) {
        return booking.getStatus() == BookingStatus.CANCELED
                || booking.getStatus() == BookingStatus.REJECTED
                || booking.getStatus() == BookingStatus.DONE;
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
        String value = firstNonBlank(
                booking.getDeviceName(),
                firstNonBlank(
                        booking.getRequestedServiceName(),
                        firstNonBlank(booking.getComplaintNote(),
                                "Outdoor service - " + booking.getCustomer().getName())));
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

    private static String limited(String value, int maxLength, String label) {
        String trimmed = trimToNull(value);
        if (trimmed != null && trimmed.length() > maxLength) {
            throw new IllegalArgumentException(label + " is too long");
        }
        return trimmed;
    }

    private static String firstNonBlank(String first, String fallback) {
        String value = trimToNull(first);
        return value != null ? value : trimToNull(fallback);
    }

    private void broadcast(String event) {
        dataEventPublisher.publishTopic("/topic/booking", event);
    }
}
