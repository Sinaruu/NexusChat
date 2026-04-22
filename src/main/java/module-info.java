module dev.sinaruu.nexuschat {
    requires javafx.controls;
    requires javafx.fxml;


    opens dev.sinaruu.nexuschat to javafx.fxml;
    exports dev.sinaruu.nexuschat;
}