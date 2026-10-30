-- PROJECT 28: RESTAURANT KITCHEN DISPLAY SYSTEM (KDS) & ORDER PIPELINE
-- MySQL 8 Normalized Schema Script

CREATE DATABASE IF NOT EXISTS `restaurant_kds` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE `restaurant_kds`;

-- 1. Users Table
CREATE TABLE IF NOT EXISTS `users` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `username` VARCHAR(50) NOT NULL UNIQUE,
    `password` VARCHAR(255) NOT NULL,
    `full_name` VARCHAR(100) NOT NULL,
    `email` VARCHAR(100),
    `role` VARCHAR(30) NOT NULL,
    `active` BOOLEAN NOT NULL DEFAULT TRUE,
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX `idx_user_username` (`username`),
    INDEX `idx_user_role` (`role`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 2. Kitchen Stations Table
CREATE TABLE IF NOT EXISTS `kitchen_stations` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `name` VARCHAR(50) NOT NULL UNIQUE,
    `code` VARCHAR(20) NOT NULL UNIQUE,
    `description` VARCHAR(255),
    `color_code` VARCHAR(20) DEFAULT '#0d6efd',
    `active` BOOLEAN NOT NULL DEFAULT TRUE,
    `display_order` INT NOT NULL DEFAULT 0,
    INDEX `idx_station_code` (`code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 3. Menu Categories Table
CREATE TABLE IF NOT EXISTS `menu_categories` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `name` VARCHAR(50) NOT NULL UNIQUE,
    `description` VARCHAR(255),
    `display_order` INT NOT NULL DEFAULT 0,
    `active` BOOLEAN NOT NULL DEFAULT TRUE,
    INDEX `idx_category_name` (`name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 4. Menu Items Table
CREATE TABLE IF NOT EXISTS `menu_items` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `name` VARCHAR(100) NOT NULL,
    `description` VARCHAR(500),
    `category_id` BIGINT NOT NULL,
    `price` DECIMAL(10,2) NOT NULL,
    `prep_time_minutes` INT NOT NULL DEFAULT 10,
    `station_id` BIGINT NOT NULL,
    `available` BOOLEAN NOT NULL DEFAULT TRUE,
    `stock_quantity` INT NOT NULL DEFAULT 50,
    `image_url` VARCHAR(300),
    `is_vegetarian` BOOLEAN NOT NULL DEFAULT FALSE,
    `default_modifiers` VARCHAR(300),
    `active` BOOLEAN NOT NULL DEFAULT TRUE,
    CONSTRAINT `fk_menu_category` FOREIGN KEY (`category_id`) REFERENCES `menu_categories` (`id`),
    CONSTRAINT `fk_menu_station` FOREIGN KEY (`station_id`) REFERENCES `kitchen_stations` (`id`),
    INDEX `idx_item_category` (`category_id`),
    INDEX `idx_item_station` (`station_id`),
    INDEX `idx_item_available` (`available`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 5. Orders Table
CREATE TABLE IF NOT EXISTS `orders` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `order_number` VARCHAR(32) NOT NULL UNIQUE,
    `order_type` VARCHAR(20) NOT NULL DEFAULT 'DINE_IN',
    `table_number` VARCHAR(30),
    `customer_name` VARCHAR(100),
    `customer_phone` VARCHAR(30),
    `created_by_user_id` BIGINT,
    `status` VARCHAR(30) NOT NULL DEFAULT 'PLACED',
    `priority` VARCHAR(20) NOT NULL DEFAULT 'NORMAL',
    `subtotal` DECIMAL(10,2) NOT NULL DEFAULT 0.00,
    `tax_rate` DECIMAL(5,2) NOT NULL DEFAULT 5.00,
    `tax_amount` DECIMAL(10,2) NOT NULL DEFAULT 0.00,
    `discount_amount` DECIMAL(10,2) NOT NULL DEFAULT 0.00,
    `total_amount` DECIMAL(10,2) NOT NULL DEFAULT 0.00,
    `payment_status` VARCHAR(30) NOT NULL DEFAULT 'UNPAID',
    `special_instructions` VARCHAR(500),
    `rejection_reason` VARCHAR(255),
    `cancellation_reason` VARCHAR(255),
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `accepted_at` DATETIME,
    `started_at` DATETIME,
    `ready_at` DATETIME,
    `served_at` DATETIME,
    `estimated_prep_minutes` INT DEFAULT 15,
    `estimated_completion_time` DATETIME,
    `version` BIGINT DEFAULT 0,
    CONSTRAINT `fk_order_user` FOREIGN KEY (`created_by_user_id`) REFERENCES `users` (`id`) ON DELETE SET NULL,
    INDEX `idx_order_number` (`order_number`),
    INDEX `idx_order_status` (`status`),
    INDEX `idx_order_created_at` (`created_at`),
    INDEX `idx_order_type` (`order_type`),
    INDEX `idx_order_payment_status` (`payment_status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 6. Order Items Table
CREATE TABLE IF NOT EXISTS `order_items` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `order_id` BIGINT NOT NULL,
    `menu_item_id` BIGINT NOT NULL,
    `item_name` VARCHAR(100) NOT NULL,
    `quantity` INT NOT NULL DEFAULT 1,
    `unit_price` DECIMAL(10,2) NOT NULL,
    `total_price` DECIMAL(10,2) NOT NULL,
    `special_instructions` VARCHAR(300),
    `station_id` BIGINT NOT NULL,
    `preparation_status` VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    `started_at` DATETIME,
    `ready_at` DATETIME,
    CONSTRAINT `fk_order_item_order` FOREIGN KEY (`order_id`) REFERENCES `orders` (`id`) ON DELETE CASCADE,
    CONSTRAINT `fk_order_item_menu` FOREIGN KEY (`menu_item_id`) REFERENCES `menu_items` (`id`),
    CONSTRAINT `fk_order_item_station` FOREIGN KEY (`station_id`) REFERENCES `kitchen_stations` (`id`),
    INDEX `idx_order_item_order` (`order_id`),
    INDEX `idx_order_item_station` (`station_id`),
    INDEX `idx_order_item_status` (`preparation_status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 7. Order Item Modifiers Table
CREATE TABLE IF NOT EXISTS `order_item_modifiers` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `order_item_id` BIGINT NOT NULL,
    `name` VARCHAR(100) NOT NULL,
    `price` DECIMAL(10,2) NOT NULL DEFAULT 0.00,
    CONSTRAINT `fk_modifier_order_item` FOREIGN KEY (`order_item_id`) REFERENCES `order_items` (`id`) ON DELETE CASCADE,
    INDEX `idx_modifier_item` (`order_item_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 8. Order Status History Table
CREATE TABLE IF NOT EXISTS `order_status_history` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `order_id` BIGINT NOT NULL,
    `previous_status` VARCHAR(30),
    `new_status` VARCHAR(30) NOT NULL,
    `changed_by_user_id` BIGINT,
    `timestamp` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `remarks` VARCHAR(500),
    CONSTRAINT `fk_history_order` FOREIGN KEY (`order_id`) REFERENCES `orders` (`id`) ON DELETE CASCADE,
    CONSTRAINT `fk_history_user` FOREIGN KEY (`changed_by_user_id`) REFERENCES `users` (`id`) ON DELETE SET NULL,
    INDEX `idx_history_order` (`order_id`),
    INDEX `idx_history_timestamp` (`timestamp`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 9. Payments Table
CREATE TABLE IF NOT EXISTS `payments` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `order_id` BIGINT NOT NULL,
    `amount` DECIMAL(10,2) NOT NULL,
    `payment_method` VARCHAR(30) NOT NULL DEFAULT 'CASH',
    `payment_status` VARCHAR(30) NOT NULL DEFAULT 'PAID',
    `transaction_reference` VARCHAR(100),
    `processed_by_user_id` BIGINT,
    `payment_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `notes` VARCHAR(255),
    CONSTRAINT `fk_payment_order` FOREIGN KEY (`order_id`) REFERENCES `orders` (`id`) ON DELETE CASCADE,
    CONSTRAINT `fk_payment_user` FOREIGN KEY (`processed_by_user_id`) REFERENCES `users` (`id`) ON DELETE SET NULL,
    INDEX `idx_payment_order` (`order_id`),
    INDEX `idx_payment_time` (`payment_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 10. Audit Logs Table
CREATE TABLE IF NOT EXISTS `audit_logs` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `action` VARCHAR(60) NOT NULL,
    `user_id` BIGINT,
    `target_type` VARCHAR(50),
    `target_id` BIGINT,
    `details` VARCHAR(1000),
    `timestamp` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT `fk_audit_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE SET NULL,
    INDEX `idx_audit_timestamp` (`timestamp`),
    INDEX `idx_audit_action` (`action`),
    INDEX `idx_audit_target` (`target_type`, `target_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 11. Restaurant Settings Table
CREATE TABLE IF NOT EXISTS `restaurant_settings` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `setting_key` VARCHAR(80) NOT NULL UNIQUE,
    `setting_value` VARCHAR(500),
    `description` VARCHAR(255),
    INDEX `idx_setting_key` (`setting_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
