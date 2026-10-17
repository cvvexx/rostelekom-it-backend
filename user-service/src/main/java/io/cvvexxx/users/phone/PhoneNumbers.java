package io.cvvexxx.users.phone;

public final class PhoneNumbers {

    private static final int RU_NATIONAL_LENGTH = 10;

    private PhoneNumbers() {
    }

    public static String normalize(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new InvalidPhoneNumberException("Phone number must not be blank");
        }

        String digits = raw.replaceAll("\\D", "");

        if (digits.length() == RU_NATIONAL_LENGTH + 1 && (digits.startsWith("8") || digits.startsWith("7"))) {
            digits = "7" + digits.substring(1);
        } else if (digits.length() == RU_NATIONAL_LENGTH) {
            digits = "7" + digits;
        } else {
            throw new InvalidPhoneNumberException("Unsupported phone number format: " + raw);
        }

        return "+" + digits;
    }

    public static String mask(String normalizedPhone) {
        if (normalizedPhone == null || normalizedPhone.length() < 8) {
            return "***";
        }
        return normalizedPhone.substring(0, 5) + "***" + normalizedPhone.substring(normalizedPhone.length() - 4);
    }
}
