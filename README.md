# EmbyHelperMVVM

**EmbyHelperMVVM** là một ứng dụng desktop đa nền tảng (Windows, macOS, Linux) được xây dựng bằng JavaFX 20. Ứng dụng này hoạt động như một công cụ quản lý mạnh mẽ cho máy chủ Emby, cho phép người dùng thực hiện các thao tác xử lý và sao chép metadata hàng loạt mà giao diện web mặc định của Emby không hỗ trợ.

Dự án được xây dựng theo kiến trúc MVVM (Model-View-ViewModel) rõ ràng, kết hợp với các mẫu thiết kế (Design Patterns) như Repository, UseCase, và Strategy để đảm bảo mã nguồn dễ bảo trì, mở rộng và tuân thủ các nguyên tắc SOLID.

## Tính năng chính

Ứng dụng được chia thành hai tab chức năng chính:

### 1. Công cụ Metadata (Metadata Tools)

Tab này cho phép thực hiện các thao tác metadata chi tiết, áp dụng cho 4 loại metadata: **Studios**, **Genres**, **People**, và **Tags** (bao gồm cả tag đơn giản và tag JSON key-value).

* **Batch Copy (Sao chép hàng loạt):** Sao chép metadata (ví dụ: tất cả Genres) từ một item mẫu (Source Item) sang tất cả các item con bên trong một thư mục (Destination Parent).
* **Clear by Parent (Xóa theo thư mục):** Xóa toàn bộ metadata (ví dụ: xóa hết Studios) khỏi tất cả các item con bên trong một thư mục.
* **Clear Specific (Xóa cụ thể):** Tải danh sách tất cả metadata (ví dụ: tất cả People) trên server, cho phép người dùng chọn một metadata cụ thể (ví dụ: "Diễn viên A") và xóa người đó khỏi *tất cả* các phim trên toàn server.
* **Update Specific (Đổi tên cụ thể):** Tương tự như Xóa cụ thể, nhưng cho phép đổi tên một metadata (ví dụ: đổi tên Tag "Hành động" thành "Action").

### 2. Công cụ Hàng loạt (Batch Tools)

Tab này chứa các chức năng xử lý hàng loạt phức tạp, tập trung vào việc tự động hóa và đồng bộ hóa dữ liệu.

* **Batch Set Title & Release Date:**
    * Tự động quét tất cả item (Movie) trong một thư mục cha.
    * Chuẩn hóa `OriginalTitle` của item dựa trên tên file (ví dụ: `[Her] my-movie-001.mp4` -> `MY-MOVIE-001`).
    * Gọi một API bên ngoài (localhost:8081) để lấy `PremiereDate` (Ngày phát hành) dựa trên `OriginalTitle` đã chuẩn hóa.
    * Cập nhật `PremiereDate` và `ProductionYear` cho item trên Emby.

* **Export JSON:**
    * Xuất toàn bộ thông tin metadata của tất cả item trong một thư mục cha thành các file `.json` riêng lẻ, đặt tên file dựa trên `OriginalTitle` của item.

* **Import JSON:**
    * Quét một thư mục chứa các file `.json` (đã được export trước đó).
    * Đối với mỗi file, tìm kiếm item trên server Emby có `OriginalTitle` khớp với tên file.
    * Cập nhật (ghi đè) toàn bộ metadata (Title, Overview, Studios, People, Tags, v.v.) từ file `.json` vào item tìm thấy trên server.

* **Copy Metadata (Nâng cao):**
    * **Tìm nguồn:** Người dùng cung cấp ID thư mục nguồn, ứng dụng hiển thị danh sách item con (với ID, OriginalTitle, Đường dẫn, Số ảnh).
    * **Tìm đích:** Khi người dùng chọn một item nguồn, ứng dụng tự động tìm kiếm *toàn bộ server* các item khác có cùng `OriginalTitle` và hiển thị ở bảng đích.
    * **Thực thi sao chép:** Cho phép người dùng chọn 1 item nguồn và 1 item đích, sau đó sao chép toàn bộ:
        1.  **Metadata:** (Title, Overview, Tags, Studios, People, Genres...)
        2.  **Ảnh:** Xóa tất cả ảnh Primary và Backdrops cũ của item đích, sau đó download toàn bộ ảnh Primary/Backdrops của item nguồn và re-upload lên item đích.

### Tính năng khác

* **Đa ngôn ngữ:** Hỗ trợ Tiếng Việt (mặc định) và Tiếng Anh.
* **Lưu phiên đăng nhập:** Tự động đăng nhập lại khi mở ứng dụng.
* **Ghi nhớ kích thước cửa sổ:** Tự động lưu và khôi phục kích thước/vị trí cửa sổ sau mỗi lần chạy.

## Kiến trúc

Dự án tuân thủ chặt chẽ kiến trúc **MVVM (Model-View-ViewModel)** và các nguyên tắc SOLID.

* **`view`**: (FXML và Controller) Chỉ chịu trách nhiệm hiển thị UI và bắt sự kiện, sau đó gọi "Command" trên ViewModel.
* **`viewmodel`**: (ViewModelBase, MainViewModel, BatchViewModel...) Chứa toàn bộ trạng thái của View (dưới dạng JavaFX Properties) và logic điều khiển View. ViewModel không biết gì về View.
* **`di` (ServiceLocator)**: Sử dụng mẫu Service Locator (Singleton) để quản lý và tiêm (inject) các phụ thuộc (dependency) cho toàn bộ ứng dụng.
* **`service`**: Các dịch vụ cốt lõi (Authentication, Navigation, Localization).
* **`repository`**: (Repository Pattern) Trừu tượng hóa hoàn toàn việc truy cập dữ liệu. `IEmbyRepository` định nghĩa các hành vi, và `EmbyRepositoryImpl` triển khai chúng (bao gồm cả logic upload ảnh `okhttp` phức tạp).
* **`usecase`**: (UseCase Layer) Đóng gói các logic nghiệp vụ (business logic) phức tạp. Ví dụ: `CopyMetadataUseCase` chứa toàn bộ quy trình sao chép metadata và ảnh.
* **`usecase.metadata_strategy`**: (Strategy Pattern) Sử dụng `IMetadataStrategy` để định nghĩa các hành vi chung (Copy, Clear, Update) và các lớp `GenreStrategy`, `TagStrategy`... để triển khai các hành vi đó cho từng loại metadata cụ thể.

## Công nghệ sử dụng

* **Ngôn ngữ:** Java 20
* **Framework UI:** JavaFX 20
* **Quản lý dự án:** Maven
* **API Client:** `eemby-sdk-java` (SDK tùy chỉnh cho Emby)
* **HTTP Client:** `com.squareup.okhttp` (Sử dụng riêng cho logic upload ảnh Base64)
* **JSON:** `com.google.code.gson` và `org.json`

## Build và Chạy dự án

### Yêu cầu
* JDK 20 (hoặc cao hơn)
* Maven 3.8 (hoặc cao hơn)

### Chạy (Development)

Dự án đã tích hợp Maven Wrapper, bạn không cần cài đặt Maven.

1.  Mở Terminal hoặc Command Prompt.
2.  Di chuyển đến thư mục gốc của dự án (nơi chứa file `pom.xml`).
3.  Chạy lệnh:

    * Trên macOS/Linux:
        ```bash
        ./mvnw clean javafx:run
        ```
    * Trên Windows:
        ```bash
        ./mvnw.cmd clean javafx:run
        ```

### Đóng gói (Package)

Để tạo file `.jar` có thể chạy được:

1.  Chạy lệnh:
    * Trên macOS/Linux:
        ```bash
        ./mvnw clean package
        ```
    * Trên Windows:
        ```bash
        ./mvnw.cmd clean package
        ```
2.  Ứng dụng sẽ được đóng gói tại thư mục `target/jlink-image/bin/`. Bạn có thể chạy file thực thi (executable) trong thư mục `bin` đó.