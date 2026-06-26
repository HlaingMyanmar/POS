-- phpMyAdmin SQL Dump
-- version 5.2.1
-- https://www.phpmyadmin.net/
--
-- Host: 127.0.0.1
-- Generation Time: Feb 19, 2026 at 04:14 PM
-- Server version: 10.4.32-MariaDB
-- PHP Version: 8.0.30

SET SQL_MODE = "NO_AUTO_VALUE_ON_ZERO";
START TRANSACTION;
SET time_zone = "+00:00";


/*!40101 SET @OLD_CHARACTER_SET_CLIENT=@@CHARACTER_SET_CLIENT */;
/*!40101 SET @OLD_CHARACTER_SET_RESULTS=@@CHARACTER_SET_RESULTS */;
/*!40101 SET @OLD_COLLATION_CONNECTION=@@COLLATION_CONNECTION */;
/*!40101 SET NAMES utf8mb4 */;

--
-- Database: `ser_db`
--

-- --------------------------------------------------------

--
-- Table structure for table `chart_of_accounts`
--
--
-- Dumping data for table `chart_of_accounts`
--

INSERT INTO `chart_of_accounts` (`id`, `account_name`, `account_type`, `parent_id`, `code`) VALUES
(1, 'Current Liabilities', 'Liability', NULL, 'LIA-001'),
(2, 'Current Assets', 'Asset', NULL, 'ASS-001'),
(3, 'Operating Income', 'Income', NULL, 'INC-001'),
(4, 'Operating Expenses', 'Expense', NULL, 'EXP-001'),
(5, 'Cash in Hand', 'Asset', 2, 'ASS-002'),
(6, 'KBZ Bank Account', 'Asset', 2, 'ASS-003'),
(7, 'Accounts Receivable', 'Asset', 2, 'ASS-004'),
(8, 'Accounts Payable', 'Liability', 1, 'LIA-002'),
(9, 'Salary Payable', 'Liability', 1, 'LIA-003'),
(10, 'Office Rent', 'Expense', 4, 'EXP-002'),
(11, 'Electricity Bill', 'Expense', 4, 'EXP-003'),
(12, 'Internet Charges', 'Expense', 4, 'EXP-004'),
(13, 'Product Sales', 'Income', 3, 'INC-002'),
(14, 'Consulting Revenue', 'Income', 3, 'INC-003'),
(15, 'Share Capital', 'Equity', NULL, 'EQU-001'),
(16, 'Retained Earnings', 'Equity', NULL, 'EQU-002'),
(17, 'Sales Returns & Allowances', 'Income', 3, 'INC-004'),
(18, 'Purchase Returns & Allowances', 'Expense', 4, 'EXP-005'),
(19, 'Cost of Goods Sold', 'Expense', NULL, NULL),
(20, 'Purchases', 'Expense', 19, 'EXP-007'),
(21, 'Purchase Returns', 'Expense', 19, 'EXP-008'),
(22, 'Inventory Damage Loss', 'Expense', 19, 'EXP-009');

--
-- Indexes for dumped tables
--

--
-- Indexes for table `chart_of_accounts`
--
ALTER TABLE `chart_of_accounts`
  ADD PRIMARY KEY (`id`),
  ADD UNIQUE KEY `code` (`code`),
  ADD KEY `parent_id` (`parent_id`);

--
-- AUTO_INCREMENT for dumped tables
--

--
-- AUTO_INCREMENT for table `chart_of_accounts`
--
ALTER TABLE `chart_of_accounts`
  MODIFY `id` int(11) NOT NULL AUTO_INCREMENT, AUTO_INCREMENT=23;

--
-- Constraints for dumped tables
--

--
-- Constraints for table `chart_of_accounts`
--
ALTER TABLE `chart_of_accounts`
  ADD CONSTRAINT `chart_of_accounts_ibfk_1` FOREIGN KEY (`parent_id`) REFERENCES `chart_of_accounts` (`id`);
COMMIT;

/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
