package com.github.svitorz.sells.infrastructure.config;

import com.github.svitorz.sells.application.catalog.CategoryService;
import com.github.svitorz.sells.application.catalog.ProductService;
import com.github.svitorz.sells.application.customer.CustomerService;
import com.github.svitorz.sells.application.port.CategoryRepository;
import com.github.svitorz.sells.application.port.CustomerRepository;
import com.github.svitorz.sells.application.port.PasswordHasher;
import com.github.svitorz.sells.application.port.ProductRepository;
import com.github.svitorz.sells.application.port.SaleRepository;
import com.github.svitorz.sells.application.port.StockMovementRepository;
import com.github.svitorz.sells.application.port.TransactionRunner;
import com.github.svitorz.sells.application.port.UserRepository;
import com.github.svitorz.sells.application.sale.SaleService;
import com.github.svitorz.sells.application.session.SessionContext;
import com.github.svitorz.sells.application.stock.StockService;
import com.github.svitorz.sells.application.user.UserService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/** Creates the application services. The services themselves have no Spring annotations. */
@Configuration
public class ApplicationServicesConfig {

    @Bean
    Clock clock() {
        return Clock.systemDefaultZone();
    }

    @Bean
    SessionContext sessionContext() {
        return new SessionContext();
    }

    @Bean
    UserService userService(UserRepository users, PasswordHasher hasher, SessionContext session,
                            TransactionRunner tx) {
        return new UserService(users, hasher, session, tx);
    }

    @Bean
    CategoryService categoryService(CategoryRepository categories, SessionContext session, TransactionRunner tx) {
        return new CategoryService(categories, session, tx);
    }

    @Bean
    ProductService productService(ProductRepository products, CategoryRepository categories,
                                  StockMovementRepository movements, SessionContext session,
                                  TransactionRunner tx, Clock clock) {
        return new ProductService(products, categories, movements, session, tx, clock);
    }

    @Bean
    CustomerService customerService(CustomerRepository customers, SessionContext session, TransactionRunner tx,
                                    Clock clock) {
        return new CustomerService(customers, session, tx, clock);
    }

    @Bean
    SaleService saleService(SaleRepository sales, ProductRepository products, CustomerRepository customers,
                            UserRepository users, StockMovementRepository movements, SessionContext session,
                            TransactionRunner tx, Clock clock) {
        return new SaleService(sales, products, customers, users, movements, session, tx, clock);
    }

    @Bean
    StockService stockService(ProductRepository products, StockMovementRepository movements, UserRepository users,
                              SessionContext session, TransactionRunner tx, Clock clock) {
        return new StockService(products, movements, users, session, tx, clock);
    }
}
