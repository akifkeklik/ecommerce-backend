-- Discounts table
CREATE TABLE discounts (
    id UUID PRIMARY KEY,
    code VARCHAR(50) NOT NULL UNIQUE,
    description VARCHAR(255),
    type VARCHAR(30) NOT NULL,
    "value" DECIMAL(12, 2) NOT NULL,
    minimum_order_amount DECIMAL(12, 2),
    maximum_discount_amount DECIMAL(12, 2),
    start_date TIMESTAMP,
    end_date TIMESTAMP,
    usage_limit INTEGER,
    usage_count INTEGER DEFAULT 0,
    usage_limit_per_user INTEGER,
    is_active BOOLEAN DEFAULT TRUE,
    is_first_order_only BOOLEAN DEFAULT FALSE,
    is_deleted BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP,
    created_by VARCHAR(50),
    updated_by VARCHAR(50),
    version BIGINT DEFAULT 0
);
-- Discount products junction
CREATE TABLE discount_products (
    discount_id UUID NOT NULL REFERENCES discounts(id) ON DELETE CASCADE,
    product_id UUID NOT NULL REFERENCES products(id) ON DELETE CASCADE,
    PRIMARY KEY (discount_id, product_id)
);
-- Discount categories junction
CREATE TABLE discount_categories (
    discount_id UUID NOT NULL REFERENCES discounts(id) ON DELETE CASCADE,
    category_id UUID NOT NULL REFERENCES categories(id) ON DELETE CASCADE,
    PRIMARY KEY (discount_id, category_id)
);
-- Discount excluded products
CREATE TABLE discount_excluded_products (
    discount_id UUID NOT NULL REFERENCES discounts(id) ON DELETE CASCADE,
    product_id VARCHAR(36) NOT NULL,
    PRIMARY KEY (discount_id, product_id)
);
-- Promotions table
CREATE TABLE promotions (
    id UUID PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    description VARCHAR(500),
    type VARCHAR(30) NOT NULL,
    "value" DECIMAL(12, 2),
    start_date TIMESTAMP,
    end_date TIMESTAMP,
    is_active BOOLEAN DEFAULT TRUE,
    priority INTEGER DEFAULT 0,
    banner_image VARCHAR(500),
    badge_text VARCHAR(30),
    badge_color VARCHAR(7),
    is_deleted BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP,
    created_by VARCHAR(50),
    updated_by VARCHAR(50),
    version BIGINT DEFAULT 0
);
-- Promotion products junction
CREATE TABLE promotion_products (
    promotion_id UUID NOT NULL REFERENCES promotions(id) ON DELETE CASCADE,
    product_id UUID NOT NULL REFERENCES products(id) ON DELETE CASCADE,
    PRIMARY KEY (promotion_id, product_id)
);
-- Promotion categories junction
CREATE TABLE promotion_categories (
    promotion_id UUID NOT NULL REFERENCES promotions(id) ON DELETE CASCADE,
    category_id UUID NOT NULL REFERENCES categories(id) ON DELETE CASCADE,
    PRIMARY KEY (promotion_id, category_id)
);
-- Indexes
CREATE INDEX idx_discount_code ON discounts(code);
CREATE INDEX idx_discount_active ON discounts(is_active);
CREATE INDEX idx_discount_dates ON discounts(start_date, end_date);
CREATE INDEX idx_promotion_active ON promotions(is_active);
CREATE INDEX idx_promotion_dates ON promotions(start_date, end_date);