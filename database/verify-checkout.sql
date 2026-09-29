-- Run each of these in mysql. Every one should return what the comment says.
-- Paste into:  mysql -u root -p bolt_checkout

-- ============================================================
-- 1. Guest vs authenticated  (§48 scenario B, §32)
--    8 guest rows (NULL) + 3 authenticated rows is CORRECT.
-- ============================================================
SELECT
  CASE WHEN user_id IS NULL THEN 'guest' ELSE 'authenticated' END AS order_type,
  COUNT(*) AS rows_saved
FROM checkout_records
GROUP BY order_type;

-- ============================================================
-- 2. Every non-NULL user_id must point at a real user  (§11 FK)
--    "orphan_user_ids" must be 0. If it is not, the FK is broken.
-- ============================================================
SELECT COUNT(*) AS orphan_user_ids
FROM checkout_records c
LEFT JOIN users u ON u.id = c.user_id
WHERE c.user_id IS NOT NULL AND u.id IS NULL;

-- ============================================================
-- 3. No NULLs in any required field  (§11 "cannot be null")
--    Every count must be 0.
-- ============================================================
SELECT
  SUM(email IS NULL)            AS null_email,
  SUM(phone IS NULL)            AS null_phone,
  SUM(shipping_address IS NULL) AS null_address
FROM checkout_records;

-- ============================================================
-- 4. Email stored normalised  (§13 trim + lowercase)
--    "bad_rows" must be 0. Catches stray spaces or capitals.
-- ============================================================
SELECT COUNT(*) AS bad_rows
FROM checkout_records
WHERE email <> LOWER(TRIM(email));

-- ============================================================
-- 5. Order history survives user deletion  (§11 ON DELETE SET NULL)
--    Read the two numbers, then delete the user, then read again.
-- ============================================================
SELECT COUNT(*) AS orders_before FROM checkout_records WHERE user_id IS NOT NULL;
-- DELETE FROM users WHERE id = <the_id>;
SELECT COUNT(*) AS orders_after FROM checkout_records WHERE user_id IS NOT NULL;
-- orders_after must be 0, and the total row count must be unchanged.

-- ============================================================
-- 6. OTP is never stored in plaintext  (§12)
--    "six_char_hashes" must be 0. BCrypt hashes are 60 chars.
-- ============================================================
SELECT COUNT(*) AS six_char_hashes FROM users WHERE LENGTH(otp_hash) = 6;
SELECT id, email, LEFT(otp_hash, 7) AS hash_prefix, LENGTH(otp_hash) AS len
FROM users;

-- ============================================================
-- 7. Your data, per order  (the check you were asking about)
-- ============================================================
SELECT
  c.id,
  CASE WHEN c.user_id IS NULL THEN 'GUEST' ELSE 'LOGGED IN' END AS who,
  c.user_id,
  c.email,
  (SELECT COUNT(*) FROM users u WHERE u.email = c.email) AS email_is_registered
FROM checkout_records c
ORDER BY c.id;
