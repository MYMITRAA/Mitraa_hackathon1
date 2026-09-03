package com.mitraa.hackathon.admin;

import com.mitraa.hackathon.registration.Registration;
import com.mitraa.hackathon.registration.RegistrationRepository;
import com.mitraa.hackathon.user.User;
import com.mitraa.hackathon.user.UserRepository;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;

@Service
public class AdminUserExcelService {

    private static final DateTimeFormatter DATE_TIME =
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss").withZone(ZoneOffset.UTC);

    private final UserRepository users;
    private final RegistrationRepository registrations;

    public AdminUserExcelService(UserRepository users, RegistrationRepository registrations) {
        this.users = users;
        this.registrations = registrations;
    }

    @Transactional(readOnly = true)
    public byte[] exportAllUsers(boolean includeSuperAdmins) {
        List<User> orderedUsers = users.findAll().stream()
                .filter(user -> includeSuperAdmins || user.getRole() != com.mitraa.hackathon.user.Role.SUPER_ADMIN)
                .sorted(Comparator.comparing(User::getCreatedAt).reversed())
                .toList();

        try (Workbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {

            Sheet sheet = workbook.createSheet("All Users");
            sheet.createFreezePane(0, 1);
            sheet.setAutoFilter(new org.apache.poi.ss.util.CellRangeAddress(
                    0, Math.max(0, orderedUsers.size()), 0, 16));

            CellStyle headerStyle = workbook.createCellStyle();
            headerStyle.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex());
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            var headerFont = workbook.createFont();
            headerFont.setBold(true);
            headerFont.setColor(IndexedColors.WHITE.getIndex());
            headerStyle.setFont(headerFont);

            String[] headings = {
                    "S.No.", "Full name", "Email", "Role", "Access",
                    "Email verified", "Player ID", "Date of birth", "Phone",
                    "Country", "City", "Entry type", "Domain",
                    "Registration status", "Age verification",
                    "Guardian required", "Created (UTC)"
            };

            Row header = sheet.createRow(0);
            for (int column = 0; column < headings.length; column++) {
                var cell = header.createCell(column);
                cell.setCellValue(headings[column]);
                cell.setCellStyle(headerStyle);
            }

            for (int index = 0; index < orderedUsers.size(); index++) {
                User user = orderedUsers.get(index);
                Registration registration = registrations.findByUser(user).orElse(null);
                Row row = sheet.createRow(index + 1);

                set(row, 0, index + 1);
                set(row, 1, user.getFullName());
                set(row, 2, user.getEmail());
                set(row, 3, user.getRole().name());
                set(row, 4, user.isEnabled() ? "ENABLED" : "DISABLED");
                set(row, 5, user.getEmailVerifiedAt() == null ? "NO" : "YES");
                set(row, 6, registration == null ? "" : registration.getRegistrationCode());
                set(row, 7, registration == null || registration.getDateOfBirth() == null ? "" : registration.getDateOfBirth().toString());
                set(row, 8, registration == null ? "" : registration.getPhone());
                set(row, 9, registration == null ? "" : registration.getCountry());
                set(row, 10, registration == null ? "" : registration.getCity());
                set(row, 11, registration == null || registration.getParticipationType() == null ? "" : registration.getParticipationType().name());
                set(row, 12, registration == null ? "" : registration.getDomain());
                set(row, 13, registration == null || registration.getStatus() == null ? "" : registration.getStatus().name());
                set(row, 14, registration == null ? "" : registration.getAgeVerificationStatus());
                set(row, 15, registration != null && registration.isGuardianConsentRequired() ? "YES" : "NO");
                set(row, 16, user.getCreatedAt() == null ? "" : DATE_TIME.format(user.getCreatedAt()));
            }

            for (int column = 0; column < headings.length; column++) {
                sheet.autoSizeColumn(column);
                sheet.setColumnWidth(column, Math.min(sheet.getColumnWidth(column) + 700, 16000));
            }

            workbook.write(output);
            return output.toByteArray();
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to generate the users Excel file.", exception);
        }
    }

    public byte[] exportRows(String sheetName, List<String> headings, List<List<Object>> rows) {
        try (Workbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {

            Sheet sheet = workbook.createSheet(safeSheetName(sheetName));
            sheet.createFreezePane(0, 1);

            if (!headings.isEmpty()) {
                sheet.setAutoFilter(new org.apache.poi.ss.util.CellRangeAddress(
                        0, Math.max(0, rows.size()), 0, headings.size() - 1));
            }

            CellStyle headerStyle = workbook.createCellStyle();
            headerStyle.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex());
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            var headerFont = workbook.createFont();
            headerFont.setBold(true);
            headerFont.setColor(IndexedColors.WHITE.getIndex());
            headerStyle.setFont(headerFont);

            Row header = sheet.createRow(0);
            for (int column = 0; column < headings.size(); column++) {
                var cell = header.createCell(column);
                cell.setCellValue(headings.get(column));
                cell.setCellStyle(headerStyle);
            }

            for (int rowIndex = 0; rowIndex < rows.size(); rowIndex++) {
                Row row = sheet.createRow(rowIndex + 1);
                List<Object> values = rows.get(rowIndex);
                for (int column = 0; column < headings.size(); column++) {
                    set(row, column, column < values.size() ? values.get(column) : "");
                }
            }

            for (int column = 0; column < headings.size(); column++) {
                sheet.autoSizeColumn(column);
                sheet.setColumnWidth(column, Math.min(sheet.getColumnWidth(column) + 700, 18000));
            }

            workbook.write(output);
            return output.toByteArray();
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to generate the Excel file.", exception);
        }
    }

    private String safeSheetName(String value) {
        String safe = value == null ? "Export" : value.replaceAll("[\\\\/?*\\[\\]:]", "-");
        return safe.isBlank() ? "Export" : safe.substring(0, Math.min(31, safe.length()));
    }

    private void set(Row row, int column, Object value) {
        if (value instanceof Number number) {
            row.createCell(column).setCellValue(number.doubleValue());
        } else {
            row.createCell(column).setCellValue(value == null ? "" : String.valueOf(value));
        }
    }
}
